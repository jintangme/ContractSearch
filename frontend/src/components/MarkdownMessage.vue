<template>
  <div class="markdown-message" v-html="renderedHtml"></div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

const renderedHtml = computed(() => renderMarkdown(props.content))

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function escapeAttr(value) {
  return escapeHtml(value).replace(/[^a-zA-Z0-9_-]/g, '')
}

function renderInline(value) {
  const parts = String(value ?? '').split('`')
  return parts.map((part, index) => {
    const escaped = escapeHtml(part)
    if (index % 2 === 1) return `<code>${escaped}</code>`
    return escaped
      .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/\*([^*]+)\*/g, '<em>$1</em>')
  }).join('')
}

function isTableDivider(line) {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line)
}

function splitTableRow(line) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map(cell => cell.trim())
}

function renderTable(lines) {
  const headers = splitTableRow(lines[0])
  const rows = lines.slice(2).map(splitTableRow)
  return [
    '<table>',
    '<thead><tr>',
    ...headers.map(header => `<th>${renderInline(header)}</th>`),
    '</tr></thead>',
    '<tbody>',
    ...rows.map(row => `<tr>${row.map(cell => `<td>${renderInline(cell)}</td>`).join('')}</tr>`),
    '</tbody></table>'
  ].join('')
}

function isBlockStart(line, nextLine = '') {
  return /^```/.test(line)
    || /^#{1,3}\s+/.test(line)
    || /^\s*[-*]\s+/.test(line)
    || /^\s*\d+\.\s+/.test(line)
    || (line.includes('|') && isTableDivider(nextLine))
}

function renderMarkdown(markdown) {
  const lines = String(markdown ?? '').replace(/\r\n?/g, '\n').split('\n')
  const html = []
  let index = 0
  let orderedListNext = 1

  while (index < lines.length) {
    const line = lines[index]
    const nextLine = lines[index + 1] || ''

    if (!line.trim()) {
      index += 1
      continue
    }

    if (/^```/.test(line)) {
      orderedListNext = 1
      const lang = escapeAttr(line.replace(/^```/, '').trim())
      const codeLines = []
      index += 1
      while (index < lines.length && !/^```/.test(lines[index])) {
        codeLines.push(lines[index])
        index += 1
      }
      if (index < lines.length) index += 1
      html.push(`<pre><code${lang ? ` class="language-${lang}"` : ''}>${escapeHtml(codeLines.join('\n'))}</code></pre>`)
      continue
    }

    const headingMatch = line.match(/^(#{1,3})\s+(.+)$/)
    if (headingMatch) {
      orderedListNext = 1
      const level = headingMatch[1].length
      html.push(`<h${level}>${renderInline(headingMatch[2].trim())}</h${level}>`)
      index += 1
      continue
    }

    if (line.includes('|') && isTableDivider(nextLine)) {
      orderedListNext = 1
      const tableLines = [line, nextLine]
      index += 2
      while (index < lines.length && lines[index].includes('|') && lines[index].trim()) {
        tableLines.push(lines[index])
        index += 1
      }
      html.push(renderTable(tableLines))
      continue
    }

    if (/^\s*[-*]\s+/.test(line)) {
      const items = []
      while (index < lines.length && /^\s*[-*]\s+/.test(lines[index])) {
        items.push(lines[index].replace(/^\s*[-*]\s+/, ''))
        index += 1
      }
      html.push(`<ul>${items.map(item => `<li>${renderInline(item)}</li>`).join('')}</ul>`)
      continue
    }

    if (/^\s*\d+\.\s+/.test(line)) {
      const items = []
      const requestedStart = Number(line.match(/^\s*(\d+)\./)?.[1] || 1)
      while (index < lines.length && /^\s*\d+\.\s+/.test(lines[index])) {
        items.push(lines[index].replace(/^\s*\d+\.\s+/, ''))
        index += 1
      }
      const start = requestedStart === 1 && orderedListNext > 1 ? orderedListNext : requestedStart
      const startAttr = start > 1 ? ` start="${start}"` : ''
      html.push(`<ol${startAttr}>${items.map(item => `<li>${renderInline(item)}</li>`).join('')}</ol>`)
      orderedListNext = start + items.length
      continue
    }

    const paragraph = [line.trim()]
    index += 1
    while (index < lines.length && lines[index].trim() && !isBlockStart(lines[index], lines[index + 1] || '')) {
      paragraph.push(lines[index].trim())
      index += 1
    }
    html.push(`<p>${renderInline(paragraph.join(' '))}</p>`)
  }

  return html.join('')
}
</script>

<style scoped>
.markdown-message {
  color: #1f2937;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.markdown-message :deep(p) {
  margin: 0 0 10px;
}

.markdown-message :deep(h1),
.markdown-message :deep(h2),
.markdown-message :deep(h3) {
  color: #111827;
  letter-spacing: 0;
}

.markdown-message :deep(h1) {
  margin: 0 0 14px;
  font-size: 20px;
  line-height: 1.35;
  font-weight: 750;
}

.markdown-message :deep(h2) {
  margin: 18px 0 10px;
  font-size: 17px;
  line-height: 1.4;
  font-weight: 730;
}

.markdown-message :deep(h3) {
  margin: 14px 0 8px;
  font-size: 15px;
  line-height: 1.4;
  font-weight: 680;
}

.markdown-message :deep(p:last-child),
.markdown-message :deep(ul:last-child),
.markdown-message :deep(ol:last-child),
.markdown-message :deep(pre:last-child),
.markdown-message :deep(table:last-child),
.markdown-message :deep(h1:last-child),
.markdown-message :deep(h2:last-child),
.markdown-message :deep(h3:last-child) {
  margin-bottom: 0;
}

.markdown-message :deep(strong) {
  color: #111827;
  font-weight: 750;
}

.markdown-message :deep(em) {
  color: #344054;
}

.markdown-message :deep(a) {
  color: #111827;
  font-weight: 650;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.markdown-message :deep(code) {
  padding: 2px 5px;
  border-radius: 6px;
  background: #f2f4f7;
  color: #111827;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 0.92em;
}

.markdown-message :deep(pre) {
  margin: 10px 0 12px;
  padding: 12px;
  overflow-x: auto;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  background: #f8fafc;
}

.markdown-message :deep(pre code) {
  display: block;
  padding: 0;
  background: transparent;
  white-space: pre;
  line-height: 1.55;
}

.markdown-message :deep(ul),
.markdown-message :deep(ol) {
  margin: 6px 0 12px;
  padding-left: 22px;
}

.markdown-message :deep(li + li) {
  margin-top: 4px;
}

.markdown-message :deep(table) {
  width: 100%;
  margin: 10px 0 12px;
  border-collapse: collapse;
  font-size: 13px;
}

.markdown-message :deep(th),
.markdown-message :deep(td) {
  padding: 8px 10px;
  border: 1px solid #e4e7ec;
  text-align: left;
  vertical-align: top;
}

.markdown-message :deep(th) {
  background: #f8fafc;
  color: #344054;
  font-weight: 750;
}
</style>
