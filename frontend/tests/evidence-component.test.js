import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick } from 'vue'
import { prepareEvidence } from '../src/features/conversation/evidence.js'

// Exercise the real Vue component without a browser or an additional DOM library.
const source = await readFile(new URL('../src/features/conversation/EvidenceSources.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const compiled = compileScript(descriptor, { id: 'evidence-test', inlineTemplate: true }).content
  .replace(/from (['"])vue\1/g, `from ${JSON.stringify(import.meta.resolve('vue'))}`)
  .replace(/from (['"])\.\/evidence\1/g, `from ${JSON.stringify(new URL('../src/features/conversation/evidence.js', import.meta.url).href)}`)
const { default: EvidenceSources } = await import(`data:text/javascript,${encodeURIComponent(compiled)}`)

function mountSources(items) {
  const node = (tag, text = '') => ({ tag, text, props: {}, children: [], parent: null })
  const renderer = createRenderer({
    createElement: tag => node(tag),
    createText: text => node('#text', text),
    createComment: text => node('#comment', text),
    setText: (target, text) => { target.text = text },
    setElementText: (target, text) => { target.text = text; target.children = [] },
    patchProp: (target, key, _previous, next) => { target.props[key] = next },
    parentNode: target => target.parent,
    nextSibling: target => target.parent?.children[target.parent.children.indexOf(target) + 1] || null,
    insert(target, parent, anchor = null) {
      if (target.parent) target.parent.children.splice(target.parent.children.indexOf(target), 1)
      const index = anchor ? parent.children.indexOf(anchor) : -1
      parent.children.splice(index < 0 ? parent.children.length : index, 0, target)
      target.parent = parent
    },
    remove(target) {
      target.parent?.children.splice(target.parent.children.indexOf(target), 1)
      target.parent = null
    },
  })
  const root = node('root')
  const app = renderer.createApp(EvidenceSources, { items })
  app.component('RouterLink', { props: ['to'], setup: (props, { slots }) => () => h('a', { href: props.to }, slots.default?.()) })
  app.mount(root)
  const all = () => {
    const visit = target => [target, ...target.children.flatMap(visit)]
    return visit(root)
  }
  return { app, all }
}

test('the source list expands past five items and can collapse again', async () => {
  const items = prepareEvidence(Array.from({ length: 8 }, (_, index) => ({
    id: String(index), title: 'Token', sourceRef: `contract:${index + 1}`, excerpt: `line ${index + 1}`,
  })))
  const view = mountSources(items)
  const sources = () => view.all().filter(item => item.props.class === 'source-item')
  const button = () => view.all().find(item => item.tag === 'button')
  assert.equal(sources().length, 5)
  assert.equal(button().props['aria-expanded'], false)
  button().props.onClick()
  await nextTick()
  assert.equal(sources().length, 8)
  assert.equal(button().props['aria-expanded'], true)
  assert.equal(view.all().filter(item => item.tag === 'a').length, 8)
  button().props.onClick()
  await nextTick()
  assert.equal(sources().length, 5)
  view.app.unmount()
})

test('external sources retain excerpts and safe new-tab attributes', () => {
  const excerpt = '<script>example input remains text</script>'
  const view = mountSources(prepareEvidence([{ id: 'source', title: 'Specification', excerpt, sourceRef: 'https://example.com/spec', metadata: { filePath: 'spec.md', lineStart: 10, lineEnd: 12 } }]))
  const anchor = view.all().find(item => item.tag === 'a')
  assert.equal(anchor.props.href, 'https://example.com/spec')
  assert.equal(anchor.props.rel, 'noopener noreferrer')
  assert.equal(anchor.props.target, '_blank')
  assert.ok(view.all().some(item => item.tag === 'p' && item.text === excerpt))
  assert.ok(view.all().some(item => item.tag === 'small' && item.text === 'spec.md · L10–L12'))
  view.app.unmount()
})
