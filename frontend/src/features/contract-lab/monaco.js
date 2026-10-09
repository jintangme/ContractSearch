// Shared registration is retained across visits; each view owns its editor instance.
let solidityLanguageRegistered = false
let contractLabThemeRegistered = false
let editorWorkerRegistered = false
let monacoLoadPromise = null

export function registerSolidityLanguage(monaco) {
  if (!monaco) return

  if (!solidityLanguageRegistered) {
    if (!monaco.languages.getLanguages().some(language => language.id === 'solidity')) {
      monaco.languages.register({ id: 'solidity' })
    }

    monaco.languages.setMonarchTokensProvider('solidity', {
      defaultToken: '',
      tokenPostfix: '.sol',
      keywords: [
        'pragma', 'contract', 'function', 'returns', 'public', 'private', 'view', 'pure',
        'payable', 'event', 'emit', 'mapping', 'struct', 'modifier', 'constructor',
        'if', 'else', 'return', 'require', 'uint256', 'uint8', 'address', 'string',
        'bool', 'true', 'false', 'indexed'
      ],
      tokenizer: {
        root: [
          [/\/\/.*$/, 'comment'],
          [/\/\*/, 'comment', '@comment'],
          [/"([^"\\]|\\.)*$/, 'string.invalid'],
          [/"/, 'string', '@string_double'],
          [/'([^'\\]|\\.)*$/, 'string.invalid'],
          [/'/, 'string', '@string_single'],
          [/\b\d+(\.\d+)?\b/, 'number'],
          [/[a-zA-Z_$][\w$]*/, {
            cases: {
              '@keywords': 'keyword',
              '@default': 'identifier'
            }
          }],
          [/[{}()[\]]/, '@brackets'],
          [/[;,.]/, 'delimiter'],
          [/[+\-*/%=!<>|&]+/, 'operator']
        ],
        comment: [
          [/[^/*]+/, 'comment'],
          [/\*\//, 'comment', '@pop'],
          [/[/*]/, 'comment']
        ],
        string_double: [
          [/[^\\"]+/, 'string'],
          [/\\./, 'string.escape'],
          [/"/, 'string', '@pop']
        ],
        string_single: [
          [/[^\\']+/, 'string'],
          [/\\./, 'string.escape'],
          [/'/, 'string', '@pop']
        ]
      }
    })
    solidityLanguageRegistered = true
  }

  if (!contractLabThemeRegistered) {
    monaco.editor.defineTheme('contractLabLight', {
      base: 'vs',
      inherit: true,
      rules: [
        { token: 'comment', foreground: '6b7280' },
        { token: 'keyword', foreground: '111827' },
        { token: 'string', foreground: '7c4a03' },
        { token: 'number', foreground: '1d4ed8' },
        { token: 'operator', foreground: '475467' },
        { token: 'identifier', foreground: '1f2937' }
      ],
      colors: {
        'editor.background': '#ffffff',
        'editor.foreground': '#111827',
        'editorLineNumber.foreground': '#98a2b3',
        'editorLineNumber.activeForeground': '#111827',
        'editor.selectionBackground': '#dbeafe',
        'editor.inactiveSelectionBackground': '#eef2ff',
        'editorCursor.foreground': '#111827',
        'editor.lineHighlightBackground': '#f8fafc',
        'editorGutter.background': '#ffffff',
        'editorWidget.background': '#ffffff',
        'editorWidget.border': '#dfe3e8'
      }
    })
    contractLabThemeRegistered = true
  }
}

export async function loadMonaco() {
  if (!editorWorkerRegistered && !window.MonacoEnvironment) {
    const { default: EditorWorker } = await import('monaco-editor/esm/vs/editor/editor.worker?worker')
    window.MonacoEnvironment = { getWorker: () => new EditorWorker() }
    editorWorkerRegistered = true
  }
  if (!monacoLoadPromise) {
    monacoLoadPromise = import('monaco-editor/esm/vs/editor/editor.api').catch(error => {
      monacoLoadPromise = null
      throw error
    })
  }
  return monacoLoadPromise
}
