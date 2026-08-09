<template>
  <div ref="labRoot" class="contract-lab-page" :class="{ 'contract-lab-page--fullscreen': isFullscreen }">
    <header class="lab-toolbar">
      <div class="toolbar-identity">
        <strong>Contract Lab</strong>
        <div class="lab-mode-switch" aria-label="Contract Lab 工作模式">
          <button
            type="button"
            :class="{ active: activeLabMode === 'edit' }"
            @click="setLabMode('edit')"
          >
            编辑台
          </button>
          <button
            type="button"
            :class="{ active: activeLabMode === 'proof' }"
            @click="setLabMode('proof')"
          >
            证明工作台
          </button>
        </div>
        <span>{{ currentFileName }}</span>
        <label class="example-select" title="加载示例合约">
          <select :value="selectedExamplePicker" @change="handleExampleSelect">
            <option value="">示例</option>
            <option v-for="example in contractExamples" :key="example.id" :value="example.id">
              {{ example.title }}
            </option>
          </select>
        </label>
        <span>{{ compilerToolbarLabel }}</span>
      </div>
      <div class="toolbar-actions">
        <button
          v-if="activeLabMode === 'edit'"
          class="tool-button tool-button--primary"
          type="button"
          :disabled="isCompiling"
          @click="compileContract"
        >
          {{ isCompiling ? '编译中...' : '编译' }}
        </button>
        <button
          v-if="activeLabMode === 'edit'"
          class="tool-button"
          type="button"
          :disabled="!canDeploy || isDeploying"
          :title="runButtonHint"
          @click="deployCurrentContract"
        >
          {{ isDeploying ? '部署中...' : '部署' }}
        </button>
        <button v-if="activeLabMode === 'edit'" class="tool-button" type="button" @click="sendToContractCheck">智能预审</button>
        <button
          v-if="activeLabMode === 'proof'"
          class="tool-button tool-button--primary"
          type="button"
          :disabled="isCheckingLean"
          @click="checkLeanProof"
        >
          {{ isCheckingLean ? '检查中...' : '检查证明' }}
        </button>
        <button
          v-if="activeLabMode === 'proof'"
          class="tool-button"
          type="button"
          @click="insertProofTemplate"
        >
          {{ proofTemplateButtonLabel }}
        </button>
        <button class="tool-button tool-button--icon" type="button" @click="toggleFullscreen">
          <svg v-if="!isFullscreen" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M8 3H5a2 2 0 0 0-2 2v3" />
            <path d="M16 3h3a2 2 0 0 1 2 2v3" />
            <path d="M8 21H5a2 2 0 0 1-2-2v-3" />
            <path d="M16 21h3a2 2 0 0 0 2-2v-3" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M8 3v3a2 2 0 0 1-2 2H3" />
            <path d="M16 3v3a2 2 0 0 0 2 2h3" />
            <path d="M8 21v-3a2 2 0 0 0-2-2H3" />
            <path d="M16 21v-3a2 2 0 0 1 2-2h3" />
          </svg>
          <span>{{ isFullscreen ? '退出全屏' : '进入全屏' }}</span>
        </button>
      </div>
    </header>

    <p v-if="fullscreenMessage" class="fullscreen-message">{{ fullscreenMessage }}</p>
    <div v-if="labConfirm.open" class="lab-confirm">
      <strong>{{ labConfirm.title }}</strong>
      <p>{{ labConfirm.message }}</p>
      <div>
        <button type="button" @click="cancelLabConfirm">取消</button>
        <button type="button" @click="confirmLabAction">继续</button>
      </div>
    </div>

    <section
      class="lab-workbench"
      :style="workbenchStyle"
      aria-label="合约实验室工作区"
    >
      <aside class="lab-panel lab-panel--files">
        <div class="panel-head">
          <span>工作区</span>
          <div class="panel-actions">
            <button ref="createMenuButton" type="button" title="新建" @click.stop="toggleCreateMenu">+</button>
          </div>
        </div>

        <div v-if="createMenuOpen" class="workspace-menu workspace-menu--create" :style="createMenuStyle">
          <button type="button" @click="startInlineCreateFile()">新建 Solidity 文件</button>
          <button type="button" @click="startInlineCreateFolder()">新建文件夹</button>
        </div>

        <div v-if="actionMenu.open" class="workspace-menu" :style="actionMenuStyle">
          <template v-if="actionMenu.kind === 'template'">
            <button type="button" @click="createFileFromTemplate(actionMenu.file)">从模板创建文件</button>
            <button type="button" @click="previewTemplate(actionMenu.file)">预览模板</button>
            <button type="button" @click="copyWorkspaceSource(actionMenu.file)">复制模板源码</button>
          </template>
          <template v-else-if="actionMenu.kind === 'file'">
            <button type="button" @click="startInlineRenameFile(actionMenu.file)">重命名</button>
            <button type="button" @click="requestDeleteFile(actionMenu.file)">删除</button>
            <button type="button" @click="duplicateWorkspaceFile(actionMenu.file)">复制文件</button>
            <button type="button" @click="copyWorkspaceSource(actionMenu.file)">复制源码</button>
          </template>
          <template v-else-if="actionMenu.kind === 'folder'">
            <button type="button" @click="startInlineRenameFolder(actionMenu.folder)">重命名</button>
            <button type="button" @click="requestDeleteFolder(actionMenu.folder)">删除</button>
          </template>
        </div>

        <div class="tree-section">
          <p>合约文件</p>
          <div v-for="folder in editableWorkspaceFolders" :key="folder.id" class="tree-folder-block">
            <div class="tree-folder" role="button" tabindex="0" @click="toggleFolder(folder.id)">
              <span class="tree-caret">{{ folder.collapsed ? '▸' : '▾' }}</span>
              <input
                v-if="isInlineEditingFolder(folder)"
                ref="inlineInput"
                class="tree-inline-input"
                :value="inlineEdit.value"
                @input="inlineEdit.value = $event.target.value"
                @keydown.enter.prevent="finishInlineEdit(true)"
                @keydown.esc.prevent="finishInlineEdit(false)"
                @blur="finishInlineEdit(true)"
                @click.stop
              />
              <span v-else>{{ folderDisplayName(folder) }}</span>
              <button
                v-if="!folder.readonly && folder.id !== 'contracts'"
                class="tree-more"
                type="button"
                @click.stop="showFolderMenu(folder, $event)"
              >
                ···
              </button>
            </div>
            <template v-if="!folder.collapsed">
              <div v-if="isInlineCreatingInFolder(folder.id, 'folder')" class="tree-inline-row tree-inline-row--folder">
                <span class="file-indent"></span>
                <input
                  ref="inlineInput"
                  class="tree-inline-input"
                  :value="inlineEdit.value"
                  @input="inlineEdit.value = $event.target.value"
                  @keydown.enter.prevent="finishInlineEdit(true)"
                  @keydown.esc.prevent="finishInlineEdit(false)"
                  @blur="finishInlineEdit(true)"
                />
              </div>
              <div v-if="isInlineCreatingInFolder(folder.id, 'file')" class="tree-inline-row">
                <span class="file-indent"></span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                  <path d="M14 2v6h6" />
                </svg>
                <input
                  ref="inlineInput"
                  class="tree-inline-input"
                  :value="inlineEdit.value"
                  @input="inlineEdit.value = $event.target.value"
                  @keydown.enter.prevent="finishInlineEdit(true)"
                  @keydown.esc.prevent="finishInlineEdit(false)"
                  @blur="finishInlineEdit(true)"
                />
              </div>
              <div
                v-for="file in filesInFolder(folder.id)"
                :key="file.id"
                class="file-item"
                :class="{ active: activeFileId === file.id, 'file-item--readonly': file.readonly }"
                role="button"
                tabindex="0"
                @click="openWorkspaceFile(file.id)"
              >
                <span class="file-indent"></span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                  <path d="M14 2v6h6" />
                </svg>
                <input
                  v-if="isInlineEditingFile(file)"
                  ref="inlineInput"
                  class="tree-inline-input"
                  :value="inlineEdit.value"
                  @input="inlineEdit.value = $event.target.value"
                  @keydown.enter.prevent="finishInlineEdit(true)"
                  @keydown.esc.prevent="finishInlineEdit(false)"
                  @blur="finishInlineEdit(true)"
                  @click.stop
                />
                <span v-else>{{ file.name }}</span>
                <button class="tree-more" type="button" @click.stop="showFileMenu(file, $event)">···</button>
              </div>
            </template>
          </div>
        </div>

        <div class="tree-section">
          <p>模板</p>
          <div v-for="folder in templateWorkspaceFolders" :key="folder.id" class="tree-folder-block">
            <div class="tree-folder" role="button" tabindex="0" @click="toggleFolder(folder.id)">
              <span class="tree-caret">{{ folder.collapsed ? '▸' : '▾' }}</span>
              <span>{{ folderDisplayName(folder) }}</span>
            </div>
            <template v-if="!folder.collapsed">
              <div
                v-for="file in filesInFolder(folder.id)"
                :key="file.id"
                class="file-item file-item--readonly"
                role="button"
                tabindex="0"
                @click="previewTemplate(file)"
              >
                <span class="file-indent"></span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                  <path d="M14 2v6h6" />
                </svg>
                <span>{{ file.name }}</span>
                <button class="tree-more" type="button" @click.stop="showFileMenu(file, $event)">···</button>
              </div>
            </template>
          </div>
        </div>

      </aside>
      <div v-if="deleteConfirm.open" class="workspace-confirm-backdrop" @click.self="cancelDelete">
        <div class="workspace-confirm" role="dialog" aria-modal="true">
          <strong>{{ deleteConfirm.title }}</strong>
          <p>{{ deleteConfirm.message }}</p>
          <div>
            <button type="button" @click="cancelDelete">取消</button>
            <button type="button" @click="confirmDelete">删除</button>
          </div>
        </div>
      </div>
      <div
        class="splitter splitter--left"
        @mousedown="startResize('left', $event)"
      ></div>

      <main v-show="activeLabMode === 'edit'" class="lab-editor">
        <div class="editor-toolbar">
          <div class="editor-tab">
            <span class="dot"></span>
            <strong>{{ currentFileName }}</strong>
            <span>Solidity</span>
          </div>
          <button
            class="editor-save-button"
            type="button"
            title="保存到工作区并重命名"
            aria-label="保存到工作区并重命名"
            @click="saveEditorToWorkspace"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
              <path d="M17 21v-8H7v8" />
              <path d="M7 3v5h8" />
            </svg>
          </button>
        </div>

        <p v-if="editorMessage" class="editor-message">{{ editorMessage }}</p>
        <div class="editor-frame">
          <textarea
            v-if="!editorReady"
            v-model="sourceCode"
            class="fallback-editor"
            spellcheck="false"
            aria-label="Solidity 代码编辑器"
            @input="handleFallbackInput"
          ></textarea>
          <div
            ref="editorContainer"
            class="monaco-host"
            :class="{ 'monaco-host--ready': editorReady }"
          ></div>
        </div>
      </main>

      <main v-show="activeLabMode === 'proof'" class="lab-editor proof-lab-editor">
        <div class="proof-editor-panel proof-editor-panel--mode">
          <textarea
            v-model="leanSourceCode"
            class="lean-editor lean-editor--mode"
            spellcheck="false"
            aria-label="Lean4 证明编辑区"
            @input="handleLeanSourceInput"
          ></textarea>
        </div>
      </main>

      <div class="splitter splitter--right" @mousedown="startResize('right', $event)"></div>

      <aside class="lab-panel lab-panel--display universal-panel">
        <div class="universal-panel-tabs" aria-label="右侧通用标签面板">
          <div class="panel-tab-strip">
            <div
              v-for="tab in openPanelTabs"
              :key="tab"
              class="panel-tab"
              :class="{ active: activePanelTab === tab }"
              role="button"
              tabindex="0"
              @click="setActivePanelTab(tab)"
              @keydown.enter.prevent="setActivePanelTab(tab)"
            >
              <span>{{ PANEL_TAB_LABELS[tab] }}</span>
              <button type="button" title="关闭" @click.stop="closePanelTab(tab)">×</button>
            </div>
          </div>
          <div class="panel-tab-menu-wrap">
            <button type="button" class="panel-tab-add" title="打开面板" @click.stop="togglePanelMenu">+</button>
            <div v-if="panelMenuOpen" class="panel-add-menu">
              <button
                v-for="tab in PANEL_TAB_ORDER"
                :key="tab"
                type="button"
                :class="{ active: openPanelTabs.includes(tab) }"
                @click="openPanelTab(tab)"
              >
                {{ PANEL_TAB_LABELS[tab] }}
              </button>
            </div>
          </div>
        </div>

        <section class="panel-tab-content">
          <div v-if="activePanelTab === PANEL_TABS.CHAT" class="chat-panel">
            <div class="panel-chat-toolbar">
              <label class="chat-model-row">
                <AiModelSelect v-model="selectedModel" @update:model-value="persistSelectedModel" />
              </label>
              <button type="button" @click="clearChatMessages">清空</button>
            </div>

            <div v-if="attachedPrecheckReport" class="chat-attachment">
              <div class="chat-attachment-main">
                <div>
                  <strong>预审报告</strong>
                  <p>
                    {{ attachedPrecheckReport.contractName || '未命名合约' }}
                    · {{ attachedPrecheckBlockingCount }} 阻断
                    · {{ attachedPrecheckSuggestionCount }} 建议
                  </p>
                </div>
                <button type="button" class="attachment-close-btn" @click="removeAttachedPrecheckReport" aria-label="移除预审报告">
                  ×
                </button>
              </div>
              <div class="chat-attachment-actions">
                <button type="button" :disabled="isChatLoading" @click="sendAttachedPrecheckToChat">
                  {{ isChatLoading ? '生成中...' : '生成修复方案' }}
                </button>
                <button type="button" @click="precheckReportExpanded = !precheckReportExpanded">
                  {{ precheckReportExpanded ? '收起报告' : '查看报告' }}
                </button>
              </div>
              <div v-if="precheckReportExpanded" class="chat-attachment-issues">
                <div v-for="issue in attachedPrecheckPreviewIssues" :key="issue.id || `${issue.title}-${issue.line}`" class="chat-attachment-issue">
                  <span>{{ formatPrecheckSeverity(issue.severity) }} · {{ issue.line ? `Line ${issue.line}` : '未定位' }}</span>
                  <strong>{{ issue.title || '预审问题' }}</strong>
                  <p>{{ issue.message || issue.suggestion || '需要进一步确认。' }}</p>
                </div>
              </div>
            </div>

            <div ref="chatMessagesContainer" class="chat-messages">
              <div v-if="!chatMessages.length && !isChatLoading" class="chat-empty">
                可询问当前合约、解释错误，或生成修改提案。
              </div>
              <article
                v-for="(message, index) in chatMessages"
                :key="message.localId || `${message.createdAt}-${index}`"
                class="chat-message"
                :class="[`chat-message--${message.role}`, { 'chat-message--proposal': message.type === 'edit_proposal' }]"
              >
                <template v-if="message.type === 'edit_proposal'">
                  <div class="proposal-card">
                    <div class="proposal-head">
                      <div>
                        <strong>{{ message.proposal.title }}</strong>
                        <p>{{ message.proposal.summary }}</p>
                      </div>
                      <span>{{ formatProposalStatus(message.proposal) }}</span>
                    </div>
                    <div class="proposal-meta">
                      <span>{{ message.proposal.target }}</span>
                      <span>{{ message.proposal.changes.length }} 项修改</span>
                    </div>
                    <ul v-if="message.proposal.reviewOpen" class="proposal-changes">
                      <li v-for="item in message.proposal.changes" :key="item">{{ item }}</li>
                    </ul>
                    <div v-if="message.proposal.reviewOpen" class="proposal-diff">
                      <div class="proposal-diff-head">
                        <span>Diff</span>
                        <button type="button" @click="copyValue(message.proposal.newSourceCode, `proposal-${message.proposal.id}`)">
                          {{ copiedField === `proposal-${message.proposal.id}` ? '已复制' : '复制新代码' }}
                        </button>
                      </div>
                      <pre>
<template v-for="(line, diffIndex) in buildProposalDiff(message.proposal)" :key="`${message.proposal.id}-${diffIndex}`"><span :class="`diff-line diff-line--${line.type}`">{{ line.text }}</span>
</template></pre>
                    </div>
                    <div class="proposal-actions">
                      <button type="button" @click="toggleProposalReview(message.proposal)">
                        {{ message.proposal.reviewOpen ? '收起' : '查看' }}
                      </button>
                      <button type="button" :disabled="message.proposal.status !== 'pending'" @click="applyEditProposal(message.proposal)">应用</button>
                      <button type="button" :disabled="message.proposal.status !== 'pending'" @click="discardEditProposal(message.proposal)">放弃</button>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <template v-if="message.role === 'assistant' && message.loading">
                    <div class="chat-loading-bubble">
                      <span>正在分析</span>
                      <i></i>
                      <i></i>
                      <i></i>
                    </div>
                  </template>
                  <template v-else-if="message.role === 'assistant'">
                    <MarkdownMessage :content="message.content" />
                    <p v-if="message.modelUsed" class="chat-model-used">由 {{ message.modelUsed }} 生成</p>
                  </template>
                  <template v-else>
                    <span>{{ message.role === 'user' ? 'You' : 'System' }}</span>
                    <pre>{{ message.content }}</pre>
                  </template>
                </template>
                <div v-if="message.role === 'assistant' && !message.loading && message.type !== 'edit_proposal'" class="chat-message-actions">
                  <button
                    type="button"
                    :title="copiedChatMessageId === (message.localId || message.createdAt) ? '已复制' : '复制'"
                    :aria-label="copiedChatMessageId === (message.localId || message.createdAt) ? '已复制' : '复制'"
                    @click="copyChatMessage(message)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <rect x="9" y="9" width="10" height="10" rx="2" />
                      <path d="M5 15H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    :disabled="isChatLoading"
                    title="重新生成"
                    aria-label="重新生成"
                    @click="regenerateChatMessage(message)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <path d="M21 12a9 9 0 0 1-15.5 6.2" />
                      <path d="M3 12a9 9 0 0 1 15.5-6.2" />
                      <path d="M18 3v4h-4" />
                      <path d="M6 21v-4h4" />
                    </svg>
                  </button>
                </div>
              </article>
            </div>

            <form class="chat-composer" @submit.prevent="sendChatMessage()">
              <textarea
                v-model="chatInput"
                placeholder="询问或描述你想修改的内容..."
                rows="3"
                :disabled="isChatLoading"
                @keydown.enter.exact.prevent="sendChatMessage()"
              ></textarea>
              <button type="submit" :disabled="isChatLoading || !chatInput.trim()">发送</button>
            </form>
          </div>

          <div v-else-if="activePanelTab === PANEL_TABS.RESULT" class="result-view">
            <div class="panel-section-head">
              <strong>结果</strong>
              <button type="button" class="display-action" @click="resetCompileResult()">清空</button>
            </div>

            <section class="result-section">
              <div class="result-section-head">
                <strong>编译摘要</strong>
                <span>{{ compileStatusLabel }}</span>
              </div>
              <div v-if="compileStatus === 'success'" class="compiler-report">
                <div class="report-line report-line--strong">
                  <span>编译</span>
                  <strong>Success · {{ compileDurationText }}</strong>
                </div>
                <div class="report-line">
                  <span>文件</span>
                  <strong>{{ currentFileName }}</strong>
                </div>
                <div class="report-line">
                  <span>合约</span>
                  <strong>{{ compiledContractNamesLabel }}</strong>
                </div>
                <div v-if="fileContractNameMismatch" class="compiler-compat-note compiler-compat-note--subtle">
                  <strong>文件名与合约名不同</strong>
                  <p>{{ currentFileName }} → {{ selectedContractName }}</p>
                </div>
                <div class="report-line">
                  <span>Bytecode</span>
                  <strong>{{ selectedContractBytecodeSize }}</strong>
                </div>
                <div class="report-line">
                  <span>Diagnostics</span>
                  <strong>{{ compileWarnings.length ? `${compileWarnings.length} warning` : 'No errors / No warnings' }}</strong>
                </div>
                <div class="report-line">
                  <span>Pragma</span>
                  <strong>{{ detectedPragmaText }}</strong>
                </div>
                <div class="report-line">
                  <span>编译器</span>
                  <strong>{{ selectedCompilerText }}</strong>
                </div>
              </div>

              <div v-else-if="compileStatus === 'compiling'" class="compile-loading">
                <span></span>
                <strong>正在编译...</strong>
              </div>

              <div v-else-if="compileStatus === 'idle' || compileStatus === 'modified'" class="stage-empty stage-empty--compact">
                <strong>{{ compileStatusLabel }}</strong>
                <p>{{ compileStatusHint }}</p>
              </div>

              <div v-if="compileStatus === 'failed'" class="compiler-report compiler-report--failed">
                <div class="report-line report-line--strong">
                  <span>编译</span>
                  <strong>Failed</strong>
                </div>
                <div class="report-line">
                  <span>文件</span>
                  <strong>{{ currentFileName }}</strong>
                </div>
                <div class="report-line">
                  <span>诊断信息</span>
                  <strong>{{ compileErrors.length }} error · {{ compileWarnings.length }} warning</strong>
                </div>
                <div class="report-line">
                  <span>Pragma</span>
                  <strong>{{ detectedPragmaText }}</strong>
                </div>
                <div class="report-line">
                  <span>编译器</span>
                  <strong>{{ selectedCompilerText }}</strong>
                </div>
                <div v-if="versionMismatch" class="compiler-compat-note">
                  <strong>Compiler Compatibility</strong>
                  <p>{{ compilerCompatibilityMessage }}</p>
                </div>
              </div>
            </section>

            <section v-if="compileStatus === 'success' && constructorInputs.length" class="result-section">
              <div class="result-section-head">
                <strong>Constructor</strong>
                <span>{{ constructorInputs.length }} 参数</span>
              </div>
              <div class="constructor-panel">
                <label
                  v-for="(input, index) in constructorInputs"
                  :key="`constructor-${index}`"
                  class="constructor-field"
                >
                  <span>{{ input.name || `arg${index}` }} · {{ input.type }}</span>
                  <input
                    v-model="constructorArgValues[index]"
                    :placeholder="getInputPlaceholder(input)"
                    :disabled="isDeploying"
                  />
                </label>
              </div>
            </section>

            <section v-if="deployStatus !== 'idle'" class="result-section">
              <div class="result-section-head">
                <strong>部署</strong>
                <span>{{ deployStatus === 'success' ? '已部署' : deployStatus === 'deploying' ? '部署中' : '失败' }}</span>
              </div>
              <div class="compiler-report" :class="{ 'compiler-report--failed': deployStatus === 'failed' }">
                <div class="report-line">
                  <span>状态</span>
                  <strong>{{ deployStatus }}</strong>
                </div>
                <div v-if="deployedContractAddress" class="report-line report-line--copyable">
                  <span>地址</span>
                  <button type="button" @click="copyValue(deployedContractAddress, 'contractAddress')">
                    {{ shortenHash(deployedContractAddress) }}
                    <small>{{ copiedField === 'contractAddress' ? '已复制' : '复制' }}</small>
                  </button>
                </div>
                <div v-if="deploymentTransactionHash" class="report-line report-line--copyable">
                  <span>Tx</span>
                  <button type="button" @click="copyValue(deploymentTransactionHash, 'deployTx')">
                    {{ shortenHash(deploymentTransactionHash) }}
                    <small>{{ copiedField === 'deployTx' ? '已复制' : '复制' }}</small>
                  </button>
                </div>
                <div v-if="deploymentGasUsed" class="report-line">
                  <span>Gas</span>
                  <strong>{{ deploymentGasUsed }}</strong>
                </div>
                <div v-if="deployError" class="report-line">
                  <span>错误</span>
                  <strong>{{ deployError }}</strong>
                </div>
              </div>
            </section>

            <section class="result-section">
              <div class="result-section-head">
                <strong>ABI</strong>
                <div>
                  <button type="button" :disabled="!selectedContract" @click="copyValue(selectedContractAbiPreview, 'abi')">
                    {{ copiedField === 'abi' ? '已复制' : '复制 ABI' }}
                  </button>
                  <button type="button" :disabled="!selectedContract" @click="abiExpanded = !abiExpanded">
                    {{ abiExpanded ? '折叠' : '展开' }}
                  </button>
                </div>
              </div>
              <div v-if="selectedContract" class="abi-panel">
                <div class="abi-meta">
                  <span>{{ selectedContract.contractName }}</span>
                  <strong>{{ selectedContractBytecodeSize }}</strong>
                </div>
                <p>Bytecode {{ selectedContractBytecodePreview }}</p>
                <pre v-if="abiExpanded">{{ selectedContractAbiPreview }}</pre>
              </div>
              <div v-else class="stage-empty stage-empty--compact">
                <strong>暂无 ABI</strong>
                <p>请先编译合约。</p>
              </div>
            </section>

            <section class="result-section">
              <div class="result-section-head">
                <strong>事件</strong>
                <span>{{ eventLogs.length ? `${eventLogs.length} 条` : '暂无事件' }}</span>
              </div>
              <template v-if="eventLogs.length">
                <div class="event-log-list">
                  <article v-for="(event, index) in eventLogs" :key="`${event.eventName}-${index}`" class="event-log-item">
                    <div class="event-log-title">
                      <span>#{{ eventLogs.length - index }}</span>
                      <strong>{{ event.eventName || 'Event' }}</strong>
                    </div>
                    <p v-for="(value, key) in normalizeEventArgs(event.args)" :key="key">
                      <span>{{ key }}</span>
                      <strong>{{ formatRuntimeValue(value) }}</strong>
                    </p>
                  </article>
                </div>
              </template>
              <p v-else class="result-empty-line">暂无事件。</p>
            </section>
          </div>

          <div v-else-if="activePanelTab === PANEL_TABS.INTERACTION" class="panel-interaction">
            <div v-if="deployStatus !== 'success'" class="dock-empty">
              <strong>交互</strong>
              <p>部署成功后，可以在这里调用合约函数。</p>
            </div>
            <template v-else>
              <div class="interaction-browser interaction-browser--panel">
                <aside class="function-nav">
                  <div v-if="readFunctions.length" class="function-nav-group">
                    <h3>读取函数</h3>
                    <button
                      v-for="fn in readFunctions"
                      :key="functionKey(fn)"
                      :class="{ active: selectedFunction && functionKey(selectedFunction) === functionKey(fn) }"
                      type="button"
                      @click="selectInteractionFunction(fn)"
                    >
                      <strong>{{ fn.name }}</strong>
                      <span>{{ formatFunctionSignature(fn) }}</span>
                    </button>
                  </div>
                  <div v-if="writeFunctions.length" class="function-nav-group">
                    <h3>写入函数</h3>
                    <button
                      v-for="fn in writeFunctions"
                      :key="functionKey(fn)"
                      :class="{ active: selectedFunction && functionKey(selectedFunction) === functionKey(fn) }"
                      type="button"
                      @click="selectInteractionFunction(fn)"
                    >
                      <strong>{{ fn.name }}</strong>
                      <span>{{ formatFunctionSignature(fn) }}</span>
                    </button>
                  </div>
                </aside>

                <section v-if="selectedFunction" class="function-detail">
                  <div class="function-detail-head">
                    <div>
                      <strong>{{ formatFunctionSignature(selectedFunction) }}</strong>
                      <span>{{ selectedFunctionGroup }} · {{ selectedFunction.stateMutability }}</span>
                    </div>
                  </div>

                  <div class="function-detail-body">
                    <div class="inspector-section">
                      <h3>参数</h3>
                      <div class="inspector-table">
                        <div class="inspector-row inspector-row--head">
                          <span>参数</span>
                          <span>Type</span>
                          <span>值</span>
                        </div>
                        <div v-if="!selectedFunction.inputs?.length" class="inspector-row">
                          <span>none</span>
                          <span>-</span>
                          <span class="muted-value">No parameters required</span>
                        </div>
                        <label
                          v-for="(input, index) in selectedFunction.inputs"
                          :key="`${functionKey(selectedFunction)}-${index}`"
                          class="inspector-row inspector-row--input"
                        >
                          <span>{{ input.name || `arg${index}` }} · 必填</span>
                          <span>{{ input.type }}</span>
                          <div class="inspector-value-cell">
                            <input
                              :value="getFunctionInput(selectedFunction, index)"
                              @input="updateFunctionArg(selectedFunction, index, $event.target.value)"
                              :data-function-key="functionKey(selectedFunction)"
                              :data-param-index="index"
                              :placeholder="getInputPlaceholder(input)"
                              :disabled="isFunctionCalling(selectedFunction)"
                            />
                            <small v-if="getFunctionArgError(selectedFunction, index)">{{ getFunctionArgError(selectedFunction, index) }}</small>
                          </div>
                        </label>
                        <label v-if="selectedFunction.stateMutability === 'payable'" class="inspector-row inspector-row--input">
                          <span>value</span>
                          <span>ETH</span>
                          <input
                            :value="getFunctionValue(selectedFunction)"
                            placeholder="0"
                            :disabled="isFunctionCalling(selectedFunction)"
                            @input="updateFunctionValue(selectedFunction, $event.target.value)"
                          />
                        </label>
                      </div>
                    </div>

                    <div class="function-action-row">
                      <button
                        type="button"
                        :disabled="isFunctionCalling(selectedFunction)"
                        @click="callRuntimeFunction(selectedFunction)"
                      >
                        {{ isFunctionCalling(selectedFunction)
                          ? (isReadFunction(selectedFunction) ? '调用中...' : '发送中...')
                          : (isReadFunction(selectedFunction) ? '调用函数' : '发送交易') }}
                      </button>
                    </div>

                    <div class="inspector-section">
                      <h3>执行摘要</h3>
                      <div class="inspector-table">
                        <div v-if="!getFunctionExecutionRows(selectedFunction).length" class="inspector-row">
                          <span>Status</span>
                          <span>-</span>
                          <span>尚未执行</span>
                        </div>
                        <div
                          v-for="row in getFunctionExecutionRows(selectedFunction)"
                          :key="row.label"
                          class="inspector-row"
                        >
                          <span>{{ row.label }}</span>
                          <span>{{ row.type }}</span>
                          <span class="result-value">{{ row.value }}</span>
                        </div>
                      </div>
                    </div>

                    <div v-if="getFunctionStateUpdates(selectedFunction).length" class="inspector-section">
                      <h3>状态变化</h3>
                      <div class="inspector-table">
                        <div
                          v-for="update in getFunctionStateUpdates(selectedFunction)"
                          :key="update.key"
                          class="inspector-row"
                        >
                          <span>{{ update.name }}</span>
                          <span>read</span>
                          <span class="result-value">{{ formatStateUpdate(update) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </section>
              </div>
            </template>
          </div>

          <div v-else-if="activePanelTab === PANEL_TABS.PROOF" class="result-view proof-result-panel">
            <div class="proof-result-summary" :class="`proof-result-summary--${leanCheckStatus}`">
              <strong>{{ leanResultTitle }}</strong>
              <span v-if="leanCheckElapsedMs !== null">Elapsed: {{ leanCheckElapsedMs }} ms</span>
            </div>
            <div v-if="leanServiceError" class="proof-service-message">
              {{ leanServiceError }}
            </div>
            <div v-else-if="leanCheckMessage" class="proof-service-message">
              {{ leanCheckMessage }}
            </div>
            <div v-if="leanCheckStatus === 'idle' || leanCheckStatus === 'stale'" class="proof-placeholder proof-placeholder--dock">
              <p>{{ leanCheckStatus === 'stale' ? '证明代码已修改，点击“检查证明”重新验证。' : '等待检查。' }}</p>
            </div>
            <div v-else-if="leanCheckStatus === 'checking'" class="proof-placeholder proof-placeholder--dock">
              <p>Lean4 正在验证当前证明文件。</p>
            </div>
            <div v-else-if="leanCheckStatus === 'passed' && !leanCheckMessage" class="proof-pass proof-pass--dock">
              <p>Lean proof checked successfully.</p>
            </div>
            <div v-else class="proof-diagnostics proof-diagnostics--dock">
              <article
                v-for="(item, index) in leanCheckErrors"
                :key="`lean-error-${index}`"
                class="diagnostic diagnostic--error"
              >
                <span>{{ formatLeanDiagnosticLocation(item) }}</span>
                <strong>{{ item.message }}</strong>
              </article>
            </div>
            <div v-if="leanCheckWarnings.length" class="proof-diagnostics proof-diagnostics--warnings">
              <article
                v-for="(item, index) in leanCheckWarnings"
                :key="`lean-warning-${index}`"
                class="diagnostic diagnostic--warning"
              >
                <span>{{ formatLeanDiagnosticLocation(item) }}</span>
                <strong>{{ item.message }}</strong>
              </article>
            </div>
          </div>
        </section>
      </aside>

      <section class="bottom-dock" :class="{ 'bottom-dock--collapsed': dockCollapsed }">
        <div v-if="!dockCollapsed" class="splitter splitter--dock" @mousedown="startResize('dock', $event)"></div>
        <div class="dock-head">
          <div class="dock-title">
            <strong>控制台</strong>
            <span v-if="outputMessages.length">{{ outputMessages.length }}</span>
          </div>
          <div class="dock-actions">
            <button
              type="button"
              :disabled="!outputMessages.length"
              title="清空控制台"
              aria-label="清空控制台"
              @click="clearConsoleLogs"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18" />
                <path d="M8 6V4h8v2" />
                <path d="M6 6l1 15h10l1-15" />
                <path d="M10 11v6" />
                <path d="M14 11v6" />
              </svg>
            </button>
            <button
              class="dock-toggle"
              type="button"
              :title="dockCollapsed ? '展开控制台' : '折叠控制台'"
              :aria-label="dockCollapsed ? '展开控制台' : '折叠控制台'"
              @click="toggleDock"
            >
              <svg v-if="dockCollapsed" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 15l6-6 6 6" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 9l6 6 6-6" />
              </svg>
            </button>
          </div>
        </div>

        <div v-if="!dockCollapsed" class="dock-body">
          <div class="dock-pane console-pane">
            <div v-if="shouldShowConsole" ref="consoleLinesContainer" class="console-lines">
              <article
                v-for="(line, index) in outputMessages"
                :key="`${line.timestamp || index}-${index}`"
                class="console-line"
                :class="[`console-line--${line.type || 'info'}`, { muted: line.muted }]"
              >
                <div class="console-line-main">
                  <span>{{ formatConsoleTime(line.timestamp) }}</span>
                  <strong>[{{ line.source || 'system' }}]</strong>
                  <p>{{ line.message || line.text }}</p>
                </div>
                <pre v-if="line.detail">{{ line.detail }}</pre>
              </article>
            </div>
            <div v-else class="dock-empty">
              <strong>暂无日志</strong>
            </div>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>

<script>
let solidityLanguageRegistered = false
let contractLabThemeRegistered = false
let editorWorkerRegistered = false
let monacoLoadPromise = null
</script>

<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { aiApi } from '@/api'
import AiModelSelect from '@/components/AiModelSelect.vue'
import MarkdownMessage from '@/components/MarkdownMessage.vue'
import { getStoredAiModel, persistAiModel } from '@/config/aiModels'
import { contractExamples, proofStarterTemplate, starterTemplates } from './contractLabTemplates'

const RUNTIME_API_BASE = import.meta.env.VITE_RUNTIME_API_BASE || 'http://localhost:19090'
const LEAN_API_BASE = import.meta.env.VITE_LEAN_API_BASE || 'http://localhost:19100'
const COMPILE_ENDPOINT = `${RUNTIME_API_BASE}/api/runtime/compile`
const DEPLOY_ENDPOINT = `${RUNTIME_API_BASE}/api/runtime/deploy`
const CALL_ENDPOINT = `${RUNTIME_API_BASE}/api/runtime/call`
const LEAN_CHECK_ENDPOINT = `${LEAN_API_BASE}/api/lean/check`
const MARKER_OWNER = 'contract-lab-solidity'
const PRECHECK_TRANSFER_KEY = 'contractLabPrecheckSource'
const PRECHECK_REPORT_TRANSFER_KEY = 'contractLabPrecheckReport'
const DRAFT_SOURCE_TRANSFER_KEY = 'contractLabDraftSource'
const DETAIL_TO_LAB_KEY = 'contractDetailToLabSource'
const LAB_LAYOUT_KEY = 'contractLabLayoutV1'
const LAB_WORKSPACE_KEY = 'contractLabWorkspaceV1'
const PANEL_TABS = {
  CHAT: 'chat',
  RESULT: 'result',
  INTERACTION: 'interaction',
  PROOF: 'proof'
}
const PANEL_TAB_LABELS = {
  [PANEL_TABS.CHAT]: 'CHAT',
  [PANEL_TABS.RESULT]: '结果',
  [PANEL_TABS.INTERACTION]: '交互',
  [PANEL_TABS.PROOF]: '证明'
}
const PANEL_TAB_ORDER = [
  PANEL_TABS.CHAT,
  PANEL_TABS.RESULT,
  PANEL_TABS.INTERACTION,
  PANEL_TABS.PROOF
]

const router = useRouter()
const labRoot = ref(null)
const editorContainer = ref(null)
const editorInstance = shallowRef(null)
const monacoApi = shallowRef(null)
const isFullscreen = ref(false)
const fullscreenMessage = ref('')
const editorMessage = ref('')
const editorLoading = ref(true)
const editorReady = ref(false)
const activeTemplate = ref('simple-storage')
const currentFileName = ref(contractExamples[0]?.name || 'Contract.sol')
const activeFileId = ref('file-default-example')
const workspaceFolders = ref([])
const workspaceFiles = ref([])
const workspaceWidth = ref(240)
const resultPanelWidth = ref(360)
const dockHeight = ref(280)
const resizeState = ref(null)
const createMenuOpen = ref(false)
const createMenuButton = ref(null)
const createMenuPosition = ref({ top: 0, left: 0 })
const actionMenu = ref({ open: false, kind: '', file: null, folder: null, top: 0, left: 0 })
const selectedExamplePicker = ref('')
const inlineEdit = ref({ active: false, mode: '', type: '', id: '', folderId: '', value: '' })
const inlineInput = ref(null)
const selectedFolderId = ref('contracts')
const deleteConfirm = ref({ open: false, type: '', item: null, title: '', message: '' })
const labConfirm = ref({ open: false, type: '', title: '', message: '', payload: null })
const importedSourceMeta = ref({
  sourceLabel: '',
  contractName: '',
  contractAddress: '',
  abi: ''
})
const openPanelTabs = ref([PANEL_TABS.CHAT])
const activePanelTab = ref(PANEL_TABS.CHAT)
const panelMenuOpen = ref(false)
const attachedPrecheckReport = ref(null)
const precheckReportExpanded = ref(false)
const abiExpanded = ref(false)
const isCompiling = ref(false)
const compileStatus = ref('idle')
const compileErrors = ref([])
const compileWarnings = ref([])
const compiledContracts = ref([])
const selectedContractName = ref('')
const compileDurationMs = ref(null)
const detectedPragma = ref(null)
const bundledCompilerVersion = ref('')
const selectedCompilerVersion = ref('')
const versionMismatch = ref(false)
const targetCompilerRelease = ref('')
const compilerMode = ref('auto')
const compilerModeSelection = ref('auto')
const outputMessages = ref([])
const activeLabMode = ref('edit')
const dockCollapsed = ref(false)
const isDeploying = ref(false)
const deployStatus = ref('idle')
const deployError = ref('')
const runtimeSessionId = ref('')
const deployedContractAddress = ref('')
const deployer = ref('')
const deploymentTransactionHash = ref('')
const deploymentGasUsed = ref('')
const deploymentBlockNumber = ref('')
const runtimeNetwork = ref('')
const constructorArgValues = ref([])
const copiedField = ref('')
const isCallingFunction = ref(false)
const callingFunctionKey = ref('')
const functionCallStatus = ref('idle')
const functionInputs = ref({})
const functionArgErrors = ref({})
const selectedFunctionKey = ref('')
const lastCallResult = ref(null)
const callHistory = ref([])
const readResults = ref({})
const eventLogs = ref([])
const callError = ref('')
let editorBootId = 0
let editorLoadTimer = null
let editorStartTimer = null
let layoutFrame = null
let layoutTimer = null
let pendingMessageTimer = null
let copiedTimer = null
let contentChangeDisposable = null
let compileAbortController = null
let deployAbortController = null
let callAbortController = null
let leanAbortController = null

const DEFAULT_LEAN_SOURCE = proofStarterTemplate

const templates = starterTemplates.map(item => ({
  key: item.id,
  label: item.name,
  code: item.sourceCode
}))

const defaultExample = contractExamples[0]
const sourceCode = ref(defaultExample?.sourceCode || templates[0].code)
const leanSourceCode = ref(DEFAULT_LEAN_SOURCE)
const isCheckingLean = ref(false)
const leanCheckStatus = ref('idle')
const leanCheckErrors = ref([])
const leanCheckWarnings = ref([])
const leanCheckElapsedMs = ref(null)
const leanCheckMessage = ref('')
const leanServiceError = ref('')
const chatMessages = ref([])
const chatInput = ref('')
const isChatLoading = ref(false)
const chatError = ref('')
const chatMessagesContainer = ref(null)
const copiedChatMessageId = ref('')
const chatContextMemory = ref({
  lastIntent: '',
  lastResponseMode: '',
  lastTarget: '',
  lastUserTask: '',
  lastAiSummary: '',
  lastProposal: null,
  previousContext: null
})
const consoleLinesContainer = ref(null)

const selectedModel = ref(getStoredAiModel())

const defaultWorkspaceFolders = () => [
  { id: 'contracts', name: 'contracts', collapsed: false },
  { id: 'templates', name: 'templates', collapsed: false, readonly: true }
]

const defaultWorkspaceFiles = () => [
  {
    id: 'file-default-example',
    folderId: 'contracts',
    name: defaultExample?.name || 'Contract.sol',
    content: defaultExample?.sourceCode || templates[0].code,
    meta: defaultExample ? {
      sourceLabel: 'Example Contract',
      exampleId: defaultExample.id,
      exampleName: defaultExample.title
    } : {}
  },
  ...templates.map(item => ({
    id: `template-${item.key}`,
    folderId: 'templates',
    name: item.label,
    content: item.code,
    readonly: true,
    templateKey: item.key,
    meta: {}
  }))
]

workspaceFolders.value = defaultWorkspaceFolders()
workspaceFiles.value = defaultWorkspaceFiles()

const selectedContract = computed(() => (
  compiledContracts.value.find(contract => contract.contractName === selectedContractName.value)
  || compiledContracts.value[0]
  || null
))

const activeExample = computed(() => {
  const exampleId = activeWorkspaceFile()?.meta?.exampleId || ''
  return contractExamples.find(example => example.id === exampleId) || null
})

const proofTemplateButtonLabel = computed(() => (
  activeExample.value?.proofTemplate ? '插入当前示例证明模板' : '插入证明框架'
))

const selectedContractAbiPreview = computed(() => (
  selectedContract.value ? JSON.stringify(selectedContract.value.abi || [], null, 2) : ''
))

const selectedContractBytecode = computed(() => selectedContract.value?.bytecode || '')

const selectedContractAbi = computed(() => (
  Array.isArray(selectedContract.value?.abi) ? selectedContract.value.abi : []
))

const selectedContractBytecodeSize = computed(() => {
  const bytecode = selectedContractBytecode.value
  if (!bytecode || bytecode === '0x') return '0 bytes'
  return `${Math.max(0, Math.floor((bytecode.length - 2) / 2))} bytes`
})

const selectedContractBytecodePreview = computed(() => {
  const bytecode = selectedContractBytecode.value
  if (!bytecode || bytecode === '0x') return '0x'
  return bytecode.length > 34 ? `${bytecode.slice(0, 34)}...` : bytecode
})

const compiledContractNames = computed(() => (
  compiledContracts.value
    .map(contract => contract.contractName)
    .filter(Boolean)
))

const compiledContractNamesLabel = computed(() => (
  formatContractNames(compiledContractNames.value)
))

const compileTargetLabel = computed(() => (
  formatCompileTarget(currentFileName.value, compiledContractNames.value)
))

const fileContractNameMismatch = computed(() => {
  if (!selectedContractName.value || compiledContractNames.value.length !== 1) return false
  const fileStem = currentFileName.value.replace(/\.sol$/i, '')
  return fileStem !== selectedContractName.value
})

const attachedPrecheckIssueCount = computed(() => attachedPrecheckReport.value?.issues?.length || 0)
const attachedPrecheckBlockingCount = computed(() => (
  (attachedPrecheckReport.value?.issues || []).filter(item => ['critical', 'high'].includes(String(item?.severity || '').toLowerCase())).length
))
const attachedPrecheckSuggestionCount = computed(() => Math.max(0, attachedPrecheckIssueCount.value - attachedPrecheckBlockingCount.value))
const attachedPrecheckRiskText = computed(() => formatPrecheckRisk(attachedPrecheckReport.value?.riskLevel))
const attachedPrecheckPreviewIssues = computed(() => (
  Array.isArray(attachedPrecheckReport.value?.issues)
    ? attachedPrecheckReport.value.issues.slice(0, 6)
    : []
))

const constructorInputs = computed(() => (
  selectedContractAbi.value.find(item => item.type === 'constructor')?.inputs || []
))

const contractFunctions = computed(() => (
  selectedContractAbi.value.filter(item => item.type === 'function')
))

const readFunctions = computed(() => (
  contractFunctions.value.filter(item => item.stateMutability === 'view' || item.stateMutability === 'pure')
))

const writeFunctions = computed(() => (
  contractFunctions.value.filter(item => item.stateMutability === 'nonpayable' || item.stateMutability === 'payable')
))

const interactionFunctions = computed(() => [
  ...readFunctions.value.map(fn => ({ fn, group: 'READ' })),
  ...writeFunctions.value.map(fn => ({ fn, group: 'WRITE' }))
])

const selectedFunctionEntry = computed(() => (
  interactionFunctions.value.find(item => functionKey(item.fn) === selectedFunctionKey.value)
  || interactionFunctions.value[0]
  || null
))

const selectedFunction = computed(() => selectedFunctionEntry.value?.fn || null)

const selectedFunctionGroup = computed(() => selectedFunctionEntry.value?.group || '')

const hasDeployableBytecode = computed(() => {
  const bytecode = selectedContractBytecode.value
  return Boolean(bytecode && bytecode !== '0x')
})

const hasMissingConstructorArgs = computed(() => (
  constructorInputs.value.some((input, index) => String(constructorArgValues.value[index] ?? '').trim() === '')
))

const canDeploy = computed(() => (
  compileStatus.value === 'success'
  && !isCompiling.value
  && !isDeploying.value
  && Boolean(selectedContract.value)
  && Array.isArray(selectedContractAbi.value)
  && hasDeployableBytecode.value
  && !hasMissingConstructorArgs.value
))

const runButtonHint = computed(() => {
  if (isDeploying.value) return '正在部署到 Local Sandbox'
  if (compileStatus.value === 'idle' || compileStatus.value === 'modified') return '请先编译合约'
  if (compileStatus.value === 'failed') return '请修复编译错误后再运行'
  if (!selectedContract.value) return '请先选择已编译合约'
  if (hasMissingConstructorArgs.value) return '请填写构造函数参数'
  if (!hasDeployableBytecode.value) return '当前合约没有可部署字节码'
  return '部署到 Local Sandbox'
})

const compileDurationText = computed(() => (
  typeof compileDurationMs.value === 'number' ? `${compileDurationMs.value}ms` : '-'
))

const detectedPragmaText = computed(() => (
  detectedPragma.value?.expression ? `solidity ${detectedPragma.value.expression}` : '未声明'
))

const selectedCompilerText = computed(() => (
  selectedCompilerVersion.value ? `solc ${selectedCompilerVersion.value}` : '未选择'
))

const compilerToolbarLabel = computed(() => (
  selectedCompilerVersion.value ? `solc ${selectedCompilerVersion.value}` : '编译器 Auto'
))

const workbenchStyle = computed(() => {
  const resultWidth = `${resultPanelWidth.value}px`
  const dockSize = dockCollapsed.value ? '42px' : `${dockHeight.value}px`
  return {
    '--workspace-width': `${workspaceWidth.value}px`,
    '--result-width': resultWidth,
    '--dock-height': dockSize,
    gridTemplateColumns: `${workspaceWidth.value}px 6px minmax(0, 1fr) 6px ${resultWidth}`,
    gridTemplateRows: `minmax(0, 1fr) ${dockSize}`
  }
})

const compilerCompatibilityMessage = computed(() => {
  if (!versionMismatch.value) return ''
  const required = detectedPragmaText.value
  const bundled = bundledCompilerVersion.value ? `当前内置 solc ${bundledCompilerVersion.value}` : '当前内置编译器'
  const target = targetCompilerRelease.value ? `，已尝试加载 ${targetCompilerRelease.value}` : ''
  return `源码声明 ${required}，与 ${bundled} 不兼容${target}。这通常不是合约源码错误，而是需要切换到匹配的 Solidity 编译器版本。`
})

const shouldShowConsole = computed(() => outputMessages.value.length > 0)

const lastSuccessfulWrite = computed(() => (
  callHistory.value.find(item => item.status === 'success' && item.type !== 'call') || null
))

const lastSuccessfulRead = computed(() => (
  callHistory.value.find(item => item.status === 'success' && item.type === 'call') || null
))

const leanCheckStatusLabel = computed(() => {
  const labels = {
    idle: '等待检查',
    checking: '正在检查',
    passed: '检查通过',
    failed: '检查失败',
    stale: '代码已修改'
  }
  return labels[leanCheckStatus.value] || '等待检查'
})

const leanResultTitle = computed(() => {
  const labels = {
    idle: '等待检查',
    stale: '证明代码已修改',
    checking: '检查中',
    passed: '证明检查通过',
    failed: '证明检查失败'
  }
  return labels[leanCheckStatus.value] || '等待检查'
})

const compileStatusLabel = computed(() => {
  const labels = {
    idle: '等待编译',
    compiling: '正在编译',
    success: '编译成功',
    failed: '编译失败',
    modified: '代码已修改'
  }
  return labels[compileStatus.value] || '等待编译'
})

const compileStatusHint = computed(() => {
  if (compileStatus.value === 'compiling') return '正在请求本地 Solidity runtime service'
  if (compileStatus.value === 'success') return `主合约 ${selectedContractName.value || '未生成'}`
  if (compileStatus.value === 'failed') return '请查看错误列表并修复源码'
  if (compileStatus.value === 'modified') return '源码变化后需要重新编译'
  return '点击“编译”后查看错误、警告和编译产物'
})

const compileStatusSummary = computed(() => {
  if (compileStatus.value === 'success') return '成功'
  if (compileStatus.value === 'failed') return '失败'
  if (compileStatus.value === 'compiling') return '编译中'
  if (compileStatus.value === 'modified') return '需重新编译'
  return '未编译'
})

function openPanelTab(tabKey) {
  if (!PANEL_TAB_ORDER.includes(tabKey)) return
  if (!openPanelTabs.value.includes(tabKey)) {
    openPanelTabs.value = [...openPanelTabs.value, tabKey]
  }
  activePanelTab.value = tabKey
  panelMenuOpen.value = false
}

function setActivePanelTab(tabKey) {
  if (!openPanelTabs.value.includes(tabKey)) {
    openPanelTab(tabKey)
    return
  }
  activePanelTab.value = tabKey
}

function closePanelTab(tabKey) {
  const currentIndex = openPanelTabs.value.indexOf(tabKey)
  if (currentIndex < 0) return

  const nextTabs = openPanelTabs.value.filter(tab => tab !== tabKey)
  if (!nextTabs.length) {
    openPanelTabs.value = [PANEL_TABS.CHAT]
    activePanelTab.value = PANEL_TABS.CHAT
    return
  }

  openPanelTabs.value = nextTabs
  if (activePanelTab.value === tabKey) {
    activePanelTab.value = nextTabs[Math.max(0, currentIndex - 1)] || nextTabs[0]
  }
}

function togglePanelMenu() {
  panelMenuOpen.value = !panelMenuOpen.value
}

async function toggleFullscreen() {
  fullscreenMessage.value = ''
  if (!document.fullscreenEnabled) {
    fullscreenMessage.value = '当前浏览器不支持全屏模式'
    return
  }

  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else if (labRoot.value?.requestFullscreen) {
      await labRoot.value.requestFullscreen()
    } else {
      fullscreenMessage.value = '当前浏览器不支持全屏模式'
    }
  } catch (err) {
    fullscreenMessage.value = '无法进入全屏，请检查浏览器权限设置'
  }
}

function handleFullscreenChange() {
  isFullscreen.value = document.fullscreenElement === labRoot.value
  if (isFullscreen.value) {
    fullscreenMessage.value = ''
  }
  scheduleEditorLayout()
}

function getCurrentSourceCode() {
  return editorInstance.value?.getValue() ?? sourceCode.value
}

const editableWorkspaceFolders = computed(() => (
  workspaceFolders.value.filter(folder => folder.id !== 'templates' && folder.id !== 'drafts')
))

const templateWorkspaceFolders = computed(() => (
  workspaceFolders.value.filter(folder => folder.id === 'templates')
))

const createMenuStyle = computed(() => ({
  top: `${createMenuPosition.value.top}px`,
  left: `${createMenuPosition.value.left}px`
}))

const actionMenuStyle = computed(() => ({
  top: `${actionMenu.value.top}px`,
  left: `${actionMenu.value.left}px`
}))

function folderDisplayName(folder) {
  if (folder.id === 'contracts') return 'contracts'
  if (folder.id === 'templates') return 'templates'
  return folder.name
}

function filesInFolder(folderId) {
  return workspaceFiles.value.filter(file => file.folderId === folderId)
}

function activeWorkspaceFile() {
  return workspaceFiles.value.find(file => file.id === activeFileId.value) || workspaceFiles.value.find(file => !file.readonly)
}

function isEditorPreviewMode() {
  return importedSourceMeta.value.sourceLabel === '模板预览'
    || importedSourceMeta.value.sourceLabel === '示例预览'
}

function saveActiveFileContent() {
  if (isEditorPreviewMode()) return
  const file = activeWorkspaceFile()
  if (!file || file.readonly) return
  file.content = getCurrentSourceCode()
  persistWorkspace()
}

function uniqueFileName(baseName, folderId = 'contracts', ignoreId = '') {
  const normalized = baseName.endsWith('.sol') ? baseName : `${baseName}.sol`
  const ext = '.sol'
  const stem = normalized.replace(/\.sol$/i, '')
  let candidate = normalized
  let index = 1
  const names = new Set(filesInFolder(folderId).filter(file => file.id !== ignoreId).map(file => file.name))
  while (names.has(candidate)) {
    candidate = `${stem}-${index}${ext}`
    index += 1
  }
  return candidate
}

function uniqueFolderName(baseName = 'NewFolder', ignoreId = '') {
  let candidate = baseName
  let index = 1
  const names = new Set(workspaceFolders.value.filter(folder => folder.id !== ignoreId).map(folder => folder.name))
  while (names.has(candidate)) {
    candidate = `${baseName}-${index}`
    index += 1
  }
  return candidate
}

function sanitizeSolidityFileName(value) {
  const trimmed = String(value || '').trim()
  if (!trimmed) return ''
  return trimmed.endsWith('.sol') ? trimmed : `${trimmed}.sol`
}

function basicContractTemplate(name = 'NewContract') {
  const contractName = name.replace(/\.sol$/i, '').replace(/[^A-Za-z0-9_]/g, '') || 'NewContract'
  return `// SPDX-License-Identifier: MIT\npragma solidity ^0.8.20;\n\ncontract ${contractName} {\n}\n`
}

function openWorkspaceFile(fileId) {
  const target = workspaceFiles.value.find(file => file.id === fileId)
  if (!target) return

  if (target.readonly) {
    previewTemplate(target)
    return
  }

  saveActiveFileContent()
  activeFileId.value = target.id
  selectedFolderId.value = target.folderId || 'contracts'
  currentFileName.value = target.name
  importedSourceMeta.value = target.meta || { sourceLabel: '', contractName: '', contractAddress: '', abi: '' }
  sourceCode.value = target.content || ''
  activeTemplate.value = ''

  if (editorInstance.value && editorInstance.value.getValue() !== sourceCode.value) {
    editorInstance.value.setValue(sourceCode.value)
  }

  resetCompileResult({ markModified: true })
  persistWorkspace()
  nextTick(scheduleEditorLayout)
}

function createWorkspaceFile({ folderId = 'contracts', name = 'NewContract.sol', content = null, meta = {} } = {}) {
  saveActiveFileContent()
  const fileName = uniqueFileName(name, folderId)
  const id = `file-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const nextFile = {
    id,
    folderId,
    name: fileName,
    content: content ?? basicContractTemplate(fileName),
    meta
  }
  workspaceFiles.value = [...workspaceFiles.value, nextFile]
  openWorkspaceFile(id)
  return nextFile
}

function createFileFromTemplate(templateFile) {
  if (!templateFile) return
  closeWorkspaceMenus()
  createWorkspaceFile({
    folderId: 'contracts',
    name: templateFile.name,
    content: templateFile.content,
    meta: {}
  })
  showEditorMessage('已从模板创建文件')
}

function saveEditorToWorkspace() {
  const code = getCurrentSourceCode()
  sourceCode.value = code
  const currentFile = activeWorkspaceFile()
  const isPreview = isEditorPreviewMode()

  if (currentFile && !currentFile.readonly && !isPreview) {
    currentFile.content = code
    persistWorkspace()
    startInlineRenameFile(currentFile)
    showEditorMessage('已保存到工作区')
    return
  }

  const savedFile = createWorkspaceFile({
    folderId: 'contracts',
    name: sanitizeSolidityFileName(currentFileName.value || 'NewContract.sol'),
    content: code,
    meta: importedSourceMeta.value.sourceLabel === '示例预览'
      ? {
          sourceLabel: 'Example Contract',
          exampleId: importedSourceMeta.value.exampleId || '',
          exampleName: importedSourceMeta.value.exampleName || ''
        }
      : {}
  })
  if (savedFile) {
    nextTick(() => startInlineRenameFile(savedFile))
    showEditorMessage('已保存到工作区')
  }
}

function handleExampleSelect(event) {
  const exampleId = event.target.value
  selectedExamplePicker.value = ''
  if (!exampleId) return
  const example = contractExamples.find(item => item.id === exampleId)
  if (!example) return

  saveActiveFileContent()
  sourceCode.value = example.sourceCode
  currentFileName.value = example.name
  importedSourceMeta.value = {
    sourceLabel: '示例预览',
    exampleId: example.id,
    exampleName: example.title,
    contractAddress: '',
    abi: ''
  }
  if (editorInstance.value && editorInstance.value.getValue() !== sourceCode.value) {
    editorInstance.value.setValue(sourceCode.value)
  }
  resetCompileResult({ markModified: true })
  showEditorMessage(`正在预览示例 ${example.title}，点击保存可加入工作区`)
}

function createWorkspaceFolder(name = 'NewFolder') {
  const id = `folder-${Date.now()}-${Math.random().toString(16).slice(2)}`
  workspaceFolders.value = [
    ...workspaceFolders.value,
    { id, name: uniqueFolderName(name), collapsed: false }
  ]
  persistWorkspace()
}

function toggleFolder(folderId) {
  selectedFolderId.value = folderId === 'templates' ? 'contracts' : folderId
  workspaceFolders.value = workspaceFolders.value.map(folder => (
    folder.id === folderId ? { ...folder, collapsed: !folder.collapsed } : folder
  ))
  persistWorkspace()
}

function closeWorkspaceMenus() {
  createMenuOpen.value = false
  actionMenu.value = { open: false, kind: '', file: null, folder: null, top: 0, left: 0 }
}

function positionFromEvent(event) {
  const rect = event.currentTarget.getBoundingClientRect()
  return {
    top: Math.round(rect.bottom + 6),
    left: Math.round(Math.min(rect.left, window.innerWidth - 210))
  }
}

function toggleCreateMenu(event) {
  if (createMenuOpen.value) {
    createMenuOpen.value = false
    return
  }
  createMenuPosition.value = positionFromEvent(event)
  actionMenu.value.open = false
  createMenuOpen.value = true
}

function showFileMenu(file, event) {
  closeWorkspaceMenus()
  const position = positionFromEvent(event)
  actionMenu.value = {
    open: true,
    kind: file.readonly ? 'template' : 'file',
    file,
    folder: null,
    ...position
  }
}

function showFolderMenu(folder, event) {
  closeWorkspaceMenus()
  const position = positionFromEvent(event)
  actionMenu.value = {
    open: true,
    kind: 'folder',
    file: null,
    folder,
    ...position
  }
}

function focusInlineInput() {
  nextTick(() => {
    const input = Array.isArray(inlineInput.value) ? inlineInput.value[0] : inlineInput.value
    input?.focus()
    input?.select()
  })
}

function startInlineCreateFile(folderId = selectedFolderId.value || 'contracts') {
  closeWorkspaceMenus()
  selectedFolderId.value = folderId === 'templates' ? 'contracts' : folderId
  inlineEdit.value = {
    active: true,
    mode: 'create',
    type: 'file',
    id: '',
    folderId: selectedFolderId.value,
    value: 'NewContract.sol'
  }
  workspaceFolders.value = workspaceFolders.value.map(folder => (
    folder.id === selectedFolderId.value ? { ...folder, collapsed: false } : folder
  ))
  focusInlineInput()
}

function startInlineCreateFolder() {
  closeWorkspaceMenus()
  inlineEdit.value = {
    active: true,
    mode: 'create',
    type: 'folder',
    id: '',
    folderId: 'contracts',
    value: 'NewFolder'
  }
  workspaceFolders.value = workspaceFolders.value.map(folder => (
    folder.id === 'contracts' ? { ...folder, collapsed: false } : folder
  ))
  focusInlineInput()
}

function startInlineRenameFile(file) {
  if (!file || file.readonly) return
  closeWorkspaceMenus()
  inlineEdit.value = {
    active: true,
    mode: 'rename',
    type: 'file',
    id: file.id,
    folderId: file.folderId,
    value: file.name
  }
  focusInlineInput()
}

function startInlineRenameFolder(folder) {
  if (!folder || folder.readonly || folder.id === 'contracts' || folder.id === 'templates') return
  closeWorkspaceMenus()
  inlineEdit.value = {
    active: true,
    mode: 'rename',
    type: 'folder',
    id: folder.id,
    folderId: folder.id,
    value: folder.name
  }
  focusInlineInput()
}

function isInlineCreatingInFolder(folderId, type) {
  return inlineEdit.value.active
    && inlineEdit.value.mode === 'create'
    && inlineEdit.value.type === type
    && (type === 'folder' ? folderId === 'contracts' : inlineEdit.value.folderId === folderId)
}

function isInlineEditingFile(file) {
  return inlineEdit.value.active
    && inlineEdit.value.mode === 'rename'
    && inlineEdit.value.type === 'file'
    && inlineEdit.value.id === file.id
}

function isInlineEditingFolder(folder) {
  return inlineEdit.value.active
    && inlineEdit.value.mode === 'rename'
    && inlineEdit.value.type === 'folder'
    && inlineEdit.value.id === folder.id
}

function resetInlineEdit() {
  inlineEdit.value = { active: false, mode: '', type: '', id: '', folderId: '', value: '' }
}

function finishInlineEdit(shouldCommit) {
  if (!inlineEdit.value.active) return
  const edit = { ...inlineEdit.value }
  if (!shouldCommit) {
    resetInlineEdit()
    return
  }

  const value = String(edit.value || '').trim()
  if (!value) {
    showEditorMessage(edit.type === 'file' ? '文件名不能为空' : '文件夹名不能为空')
    resetInlineEdit()
    return
  }

  if (edit.type === 'file') {
    const finalName = uniqueFileName(sanitizeSolidityFileName(value), edit.folderId, edit.id)
    if (edit.mode === 'create') {
      createWorkspaceFile({ folderId: edit.folderId, name: finalName })
    } else {
      const file = workspaceFiles.value.find(item => item.id === edit.id)
      if (file) {
        file.name = finalName
        if (activeFileId.value === file.id) currentFileName.value = file.name
        persistWorkspace()
      }
    }
  } else if (edit.type === 'folder') {
    if (edit.mode === 'create') {
      createWorkspaceFolder(value)
    } else {
      const folder = workspaceFolders.value.find(item => item.id === edit.id)
      if (folder && !folder.readonly && folder.id !== 'contracts' && folder.id !== 'templates') {
        folder.name = uniqueFolderName(value, folder.id)
        persistWorkspace()
      }
    }
  }

  resetInlineEdit()
}

function deleteWorkspaceFile(file) {
  const editableFiles = workspaceFiles.value.filter(item => !item.readonly)
  if (editableFiles.length <= 1) {
    showEditorMessage('至少保留一个 Solidity 文件')
    return
  }
  workspaceFiles.value = workspaceFiles.value.filter(item => item.id !== file.id)
  if (activeFileId.value === file.id) {
    const nextFile = workspaceFiles.value.find(item => !item.readonly)
    if (nextFile) openWorkspaceFile(nextFile.id)
  }
  persistWorkspace()
}

function deleteWorkspaceFolder(folder) {
  const childFiles = filesInFolder(folder.id)
  const remainingEditable = workspaceFiles.value.filter(item => !item.readonly && item.folderId !== folder.id)
  if (!remainingEditable.length) {
    showEditorMessage('至少保留一个 Solidity 文件')
    return
  }
  workspaceFolders.value = workspaceFolders.value.filter(item => item.id !== folder.id)
  workspaceFiles.value = workspaceFiles.value.filter(item => item.folderId !== folder.id)
  if (childFiles.some(item => item.id === activeFileId.value)) {
    openWorkspaceFile(remainingEditable[0].id)
  }
  persistWorkspace()
}

function requestDeleteFile(file) {
  closeWorkspaceMenus()
  deleteConfirm.value = {
    open: true,
    type: 'file',
    item: file,
    title: `确定删除 “${file.name}” 吗？`,
    message: '删除后该文件只会从本地工作区移除。'
  }
}

function requestDeleteFolder(folder) {
  closeWorkspaceMenus()
  const count = filesInFolder(folder.id).length
  deleteConfirm.value = {
    open: true,
    type: 'folder',
    item: folder,
    title: `确定删除 “${folder.name}” 吗？`,
    message: count ? `其中的 ${count} 个文件会一起删除。` : '该文件夹会从本地工作区移除。'
  }
}

function cancelDelete() {
  deleteConfirm.value = { open: false, type: '', item: null, title: '', message: '' }
}

function confirmDelete() {
  const pending = deleteConfirm.value
  deleteConfirm.value = { open: false, type: '', item: null, title: '', message: '' }
  if (pending.type === 'file') deleteWorkspaceFile(pending.item)
  if (pending.type === 'folder') deleteWorkspaceFolder(pending.item)
}

function handleWorkspacePointerDown(event) {
  const target = event.target
  if (target?.closest?.('.panel-add-menu') || target?.closest?.('.panel-tab-add')) return
  panelMenuOpen.value = false
  if (target?.closest?.('.workspace-menu') || target?.closest?.('.panel-actions')) return
  closeWorkspaceMenus()
}

function handleWorkspaceKeydown(event) {
  if (event.key !== 'Escape') return
  panelMenuOpen.value = false
  if (createMenuOpen.value || actionMenu.value.open) {
    closeWorkspaceMenus()
  }
  if (inlineEdit.value.active) {
    finishInlineEdit(false)
  }
  if (deleteConfirm.value.open) {
    cancelDelete()
  }
  if (labConfirm.value.open) {
    cancelLabConfirm()
  }
}

async function copyWorkspaceSource(file) {
  closeWorkspaceMenus()
  if (!file) return
  try {
    await navigator.clipboard.writeText(file.content || '')
    showEditorMessage('源码已复制')
  } catch (err) {
    showEditorMessage('复制失败，请手动选择复制')
  }
}

function duplicateWorkspaceFile(file) {
  if (!file) return
  closeWorkspaceMenus()
  createWorkspaceFile({
    folderId: file.folderId || 'contracts',
    name: file.name,
    content: file.content || '',
    meta: { ...(file.meta || {}) }
  })
}

function previewTemplate(file) {
  if (!file) return
  saveActiveFileContent()
  sourceCode.value = file.content || ''
  currentFileName.value = file.name
  importedSourceMeta.value = { sourceLabel: '模板预览', contractName: '', contractAddress: '', abi: '' }
  if (editorInstance.value && editorInstance.value.getValue() !== sourceCode.value) {
    editorInstance.value.setValue(sourceCode.value)
  }
  resetCompileResult({ markModified: true })
  showEditorMessage('正在预览模板，可从菜单创建文件')
  closeWorkspaceMenus()
}

function persistWorkspace() {
  localStorage.setItem(LAB_WORKSPACE_KEY, JSON.stringify({
    folders: workspaceFolders.value,
    files: workspaceFiles.value,
    activeFileId: activeFileId.value
  }))
}

function restoreWorkspace() {
  try {
    const raw = localStorage.getItem(LAB_WORKSPACE_KEY)
    if (!raw) return
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed.folders) && Array.isArray(parsed.files) && parsed.files.length) {
      const restoredFolders = parsed.folders
        .filter(folder => folder.id !== 'drafts')
        .map(folder => (
          folder.id === 'contracts'
            ? { ...folder, readonly: false }
            : folder
        ))
      if (!restoredFolders.some(folder => folder.id === 'contracts')) {
        restoredFolders.unshift({ id: 'contracts', name: 'contracts', collapsed: false })
      }
      if (!restoredFolders.some(folder => folder.id === 'templates')) {
        restoredFolders.push({ id: 'templates', name: 'templates', collapsed: false, readonly: true })
      }
      const restoredUserFiles = parsed.files
        .filter(file => !file.readonly && file.folderId !== 'templates')
        .map(file => (
          file.folderId === 'drafts' ? { ...file, folderId: 'contracts' } : file
        ))
      if (!restoredUserFiles.length) {
        restoredUserFiles.push(defaultWorkspaceFiles().find(file => !file.readonly))
      }
      const restoredTemplateFiles = templates.map(item => ({
        id: `template-${item.key}`,
        folderId: 'templates',
        name: item.label,
        content: item.code,
        readonly: true,
        templateKey: item.key,
        meta: {}
      }))
      workspaceFolders.value = restoredFolders
      workspaceFiles.value = [...restoredUserFiles, ...restoredTemplateFiles]
      activeFileId.value = workspaceFiles.value.some(file => file.id === parsed.activeFileId && !file.readonly)
        ? parsed.activeFileId
        : restoredUserFiles[0]?.id || workspaceFiles.value[0]?.id || ''
      const file = activeWorkspaceFile()
      if (file) {
        currentFileName.value = file.name
        sourceCode.value = file.content || ''
        importedSourceMeta.value = file.meta || {
          sourceLabel: '',
          contractName: '',
          contractAddress: '',
          abi: ''
        }
      }
    }
  } catch (err) {
    localStorage.removeItem(LAB_WORKSPACE_KEY)
  }
}

function setOutputMessages(lines) {
  if (!Array.isArray(lines) || !lines.length) return
  lines.forEach(line => appendOutputMessage(line))
}

function appendOutputMessage(line) {
  const nextLine = normalizeConsoleLog(line)
  outputMessages.value = [...outputMessages.value, nextLine].slice(-160)
  nextTick(() => {
    const target = consoleLinesContainer.value
    if (target) target.scrollTop = target.scrollHeight
    if (!dockCollapsed.value) scheduleEditorLayout()
  })
}

function normalizeConsoleLog(line) {
  if (typeof line === 'string') {
    return {
      type: 'info',
      source: 'system',
      message: line,
      timestamp: Date.now()
    }
  }

  const text = line.message || line.text || ''
  const bracketMatch = String(text).match(/^\[([^\]]+)\]\s*(.*)$/)
  const source = line.source || bracketMatch?.[1] || 'system'
  const message = line.message || bracketMatch?.[2] || text
  const inferredType = line.type
    || (source === 'warn' ? 'warning' : '')
    || (line.muted ? 'info' : 'error')

  return {
    type: inferredType,
    source: source === 'tx' ? 'call' : source,
    message,
    detail: line.detail || line.formattedMessage || '',
    timestamp: line.timestamp || Date.now(),
    muted: Boolean(line.muted)
  }
}

function clearConsoleLogs() {
  outputMessages.value = []
}

function formatConsoleTime(timestamp) {
  if (!timestamp) return '--:--:--'
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
}

function formatDiagnosticLocation(item) {
  if (item.line && item.column) return `Line ${item.line}, Column ${item.column}`
  if (item.line) return `Line ${item.line}`
  return currentFileName.value
}

function formatContractNames(names) {
  const contractNames = Array.isArray(names) ? names.filter(Boolean) : []
  if (!contractNames.length) return '未生成'
  if (contractNames.length <= 3) return contractNames.join(', ')
  return `${contractNames.slice(0, 3).join(', ')} 等 ${contractNames.length} 个合约`
}

function formatCompileTarget(fileName, names) {
  const safeFileName = fileName || 'Contract.sol'
  const contractNames = Array.isArray(names) ? names.filter(Boolean) : []
  if (!contractNames.length) return safeFileName
  if (contractNames.length === 1) return `${safeFileName} → ${contractNames[0]}`
  if (contractNames.length <= 3) return `${safeFileName} → ${contractNames.join(', ')}`
  return `${safeFileName} → ${contractNames.length} 个合约`
}

function resetCompileResult({ markModified = false } = {}) {
  compileErrors.value = []
  compileWarnings.value = []
  compiledContracts.value = []
  selectedContractName.value = ''
  compileDurationMs.value = null
  detectedPragma.value = null
  bundledCompilerVersion.value = ''
  selectedCompilerVersion.value = ''
  versionMismatch.value = false
  targetCompilerRelease.value = ''
  compilerMode.value = compilerModeSelection.value === 'auto' ? 'auto' : 'manual'
  compileStatus.value = markModified ? 'modified' : 'idle'
  resetDeployResult()
  clearCompileMarkers()
  setOutputMessages([])
}

function resetDeployResult() {
  deployStatus.value = 'idle'
  deployError.value = ''
  runtimeSessionId.value = ''
  deployedContractAddress.value = ''
  deployer.value = ''
  deploymentTransactionHash.value = ''
  deploymentGasUsed.value = ''
  deploymentBlockNumber.value = ''
  runtimeNetwork.value = ''
  copiedField.value = ''
  resetCallResult()
  deployAbortController?.abort()
  deployAbortController = null
}

function resetCallResult() {
  isCallingFunction.value = false
  callingFunctionKey.value = ''
  functionCallStatus.value = 'idle'
  functionInputs.value = {}
  functionArgErrors.value = {}
  selectedFunctionKey.value = ''
  lastCallResult.value = null
  callHistory.value = []
  readResults.value = {}
  eventLogs.value = []
  callError.value = ''
  callAbortController?.abort()
  callAbortController = null
}

function setLabMode(mode) {
  if (!['edit', 'proof'].includes(mode) || activeLabMode.value === mode) return
  if (mode === 'proof') {
    sourceCode.value = getCurrentSourceCode()
    saveActiveFileContent()
    dockCollapsed.value = false
  }
  activeLabMode.value = mode
  nextTick(scheduleEditorLayout)
}

function handleLeanSourceInput() {
  if (isCheckingLean.value) return
  if (leanCheckStatus.value === 'passed' || leanCheckStatus.value === 'failed' || leanCheckStatus.value === 'stale') {
    leanCheckStatus.value = 'stale'
  }
  leanServiceError.value = ''
  leanCheckMessage.value = ''
}

function insertProofTemplate() {
  leanSourceCode.value = activeExample.value?.proofTemplate || DEFAULT_LEAN_SOURCE
  leanCheckStatus.value = 'idle'
  leanCheckErrors.value = []
  leanCheckWarnings.value = []
  leanCheckElapsedMs.value = null
  leanCheckMessage.value = ''
  leanServiceError.value = ''
  showEditorMessage(activeExample.value?.proofTemplate ? '已插入当前示例证明模板' : '已插入通用证明框架')
}

function formatLeanDiagnosticLocation(item) {
  if (item.line && item.column) return `Line ${item.line}, Column ${item.column}`
  if (item.line) return `Line ${item.line}`
  return 'Lean'
}

function persistSelectedModel() {
  selectedModel.value = persistAiModel(selectedModel.value)
}

function scrollChatToBottom() {
  nextTick(() => {
    const target = chatMessagesContainer.value
    if (target) {
      target.scrollTop = target.scrollHeight
    }
  })
}

function appendChatMessage(role, content, extra = {}) {
  chatMessages.value = [
    ...chatMessages.value,
    {
      localId: `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      role,
      content,
      createdAt: Date.now(),
      ...extra
    }
  ]
  scrollChatToBottom()
}

function replaceChatMessage(localId, nextMessage) {
  const index = chatMessages.value.findIndex(item => (item.localId || item.createdAt) === localId)
  if (index >= 0) {
    chatMessages.value.splice(index, 1, nextMessage)
  } else {
    chatMessages.value.push(nextMessage)
  }
  scrollChatToBottom()
}

function clearChatMessages() {
  chatMessages.value = []
  chatError.value = ''
  copiedChatMessageId.value = ''
  chatContextMemory.value = {
    lastIntent: '',
    lastResponseMode: '',
    lastTarget: '',
    lastUserTask: '',
    lastAiSummary: '',
    lastProposal: null,
    previousContext: null
  }
}

function truncateForAi(value, maxLength = 12000) {
  const text = String(value || '')
  if (text.length <= maxLength) return text
  return `${text.slice(0, maxLength)}\n... [truncated]`
}

function formatPrecheckRisk(level) {
  const normalized = String(level || '').toLowerCase()
  const labels = {
    critical: '严重',
    high: '高',
    medium: '中',
    low: '低',
    info: '提示'
  }
  return labels[normalized] || level || '未标注'
}

function formatPrecheckSeverity(severity) {
  const normalized = String(severity || '').toLowerCase()
  const labels = {
    critical: 'CRITICAL',
    high: 'HIGH',
    medium: 'MEDIUM',
    low: 'LOW',
    info: 'INFO'
  }
  return labels[normalized] || 'INFO'
}

function sanitizePrecheckReport(payload) {
  if (!payload || typeof payload !== 'object') return null
  const sourceCode = String(payload.sourceCode || '')
  const issues = Array.isArray(payload.issues) ? payload.issues : []
  return {
    id: String(payload.id || `precheck-${Date.now()}`),
    contractName: String(payload.contractName || payload.fileName || '未命名合约'),
    fileName: sanitizeSolidityFileName(payload.fileName || `${payload.contractName || 'PrecheckReview'}.sol`),
    sourceCode,
    conclusion: String(payload.conclusion || ''),
    conclusionText: String(payload.conclusionText || ''),
    riskLevel: String(payload.riskLevel || ''),
    issueCount: Number(payload.issueCount || issues.length || 0),
    issues: issues.map((item, index) => ({
      id: String(item?.id || `issue-${index}`),
      severity: String(item?.severity || 'info'),
      title: String(item?.title || '预审问题'),
      message: String(item?.message || item?.suggestion || '需要进一步确认。'),
      line: Number.isFinite(Number(item?.line)) && Number(item.line) > 0 ? Number(item.line) : null,
      column: Number.isFinite(Number(item?.column)) && Number(item.column) > 0 ? Number(item.column) : null,
      suggestion: String(item?.suggestion || ''),
      source: String(item?.source || 'ai')
    })),
    createdAt: Number(payload.createdAt || Date.now())
  }
}

function openPrecheckReportSource(report) {
  if (!report?.sourceCode?.trim()) return
  const currentSource = getCurrentSourceCode()
  const targetName = sanitizeSolidityFileName(report.fileName || `${report.contractName || 'PrecheckReview'}.sol`)

  if (!currentSource.trim()) {
    sourceCode.value = report.sourceCode
    currentFileName.value = targetName
    if (editorInstance.value && editorInstance.value.getValue() !== report.sourceCode) {
      editorInstance.value.setValue(report.sourceCode)
    }
    const file = activeWorkspaceFile()
    if (file && !file.readonly) {
      file.name = targetName
      file.content = report.sourceCode
      file.meta = {
        ...(file.meta || {}),
        sourceLabel: 'Precheck Report',
        precheckReportId: report.id,
        contractName: report.contractName
      }
      persistWorkspace()
    }
    return
  }

  createWorkspaceFile({
    folderId: 'contracts',
    name: targetName,
    content: report.sourceCode,
    meta: {
      sourceLabel: 'Precheck Report',
      precheckReportId: report.id,
      contractName: report.contractName
    }
  })
}

function consumePrecheckReport() {
  let payload = null
  try {
    const raw = sessionStorage.getItem(PRECHECK_REPORT_TRANSFER_KEY)
    if (!raw) return
    sessionStorage.removeItem(PRECHECK_REPORT_TRANSFER_KEY)
    payload = JSON.parse(raw)
  } catch (err) {
    sessionStorage.removeItem(PRECHECK_REPORT_TRANSFER_KEY)
    return
  }

  const report = sanitizePrecheckReport(payload)
  if (!report) return

  attachedPrecheckReport.value = report
  precheckReportExpanded.value = false
  activeLabMode.value = 'edit'
  openPrecheckReportSource(report)
  openPanelTab(PANEL_TABS.CHAT)
  appendOutputMessage({
    type: 'info',
    source: 'precheck',
    message: `已导入预审报告：${report.contractName || report.fileName}，${report.issues.length} 个问题`
  })
}

function consumeDraftSource() {
  let payload = null
  try {
    const raw = sessionStorage.getItem(DRAFT_SOURCE_TRANSFER_KEY)
    if (!raw) return
    sessionStorage.removeItem(DRAFT_SOURCE_TRANSFER_KEY)
    payload = JSON.parse(raw)
  } catch (err) {
    sessionStorage.removeItem(DRAFT_SOURCE_TRANSFER_KEY)
    return
  }

  if (payload?.source !== 'profile-draft' || typeof payload.sourceCode !== 'string' || !payload.sourceCode.trim()) {
    return
  }

  activeLabMode.value = 'edit'
  createWorkspaceFile({
    folderId: 'contracts',
    name: sanitizeSolidityFileName(payload.fileName || `${payload.contractName || 'DraftContract'}.sol`),
    content: payload.sourceCode,
    meta: {
      sourceLabel: 'Personal Draft',
      draftRecordId: payload.recordId || '',
      contractName: payload.contractName || ''
    }
  })
  appendOutputMessage({
    type: 'info',
    source: 'system',
    message: `已导入草稿：${payload.contractName || payload.fileName || '未命名合约'}`
  })
}

function removeAttachedPrecheckReport() {
  attachedPrecheckReport.value = null
  precheckReportExpanded.value = false
}

async function sendAttachedPrecheckToChat() {
  const report = attachedPrecheckReport.value
  if (!report || isChatLoading.value) return
  openPanelTab(PANEL_TABS.CHAT)
  appendChatMessage('system', '已读取预审报告，正在生成修复方案。')
  const blockingIssues = (report.issues || []).filter(item => ['critical', 'high'].includes(String(item?.severity || '').toLowerCase()))
  const advisoryIssues = (report.issues || []).filter(item => !['critical', 'high'].includes(String(item?.severity || '').toLowerCase()))
  appendOutputMessage({
    type: 'info',
    source: 'chat',
    message: '已将预审报告发送给 CHAT'
  })
  const sent = await sendChatMessage(
    '根据预审报告生成可应用的修复方案',
    {
      mode: 'edit',
      source: 'precheck-report',
      contractName: report.contractName,
      soliditySource: truncateForAi(report.sourceCode || getCurrentSourceCode(), 12000),
      blockingIssues,
      advisoryIssues,
      lastIntent: 'fix_precheck_issues',
      precheckReport: {
        conclusion: report.conclusion,
        conclusionText: report.conclusionText,
        riskLevel: report.riskLevel,
        issues: report.issues
      }
    },
    {
      hiddenUserMessage: true,
      responseMode: 'edit_proposal',
      editTarget: 'solidity',
      intent: 'fix_precheck_issues',
      source: 'precheck_report'
    }
  )
  if (sent) {
    attachedPrecheckReport.value = null
    precheckReportExpanded.value = false
  } else {
    appendChatMessage('system', '生成失败，请重试。')
  }
}

function buildChatContext(extraContext = {}) {
  return {
    mode: activeLabMode.value,
    activeFileName: currentFileName.value,
    activeExampleId: activeExample.value?.id || '',
    activeExampleName: activeExample.value?.title || '',
    activePanelTab: activePanelTab.value,
    soliditySource: truncateForAi(getCurrentSourceCode(), 12000),
    compileStatus: compileStatus.value,
    compileErrors: compileErrors.value,
    compileWarnings: compileWarnings.value,
    compileOutput: truncateForAi(JSON.stringify({
      status: compileStatus.value,
      contractName: selectedContractName.value,
      durationMs: compileDurationMs.value,
      errors: compileErrors.value,
      warnings: compileWarnings.value,
      deployStatus: deployStatus.value,
      deployError: deployError.value
    }, null, 2), 8000),
    abi: truncateForAi(selectedContractAbiPreview.value, 8000),
    events: eventLogs.value.slice(0, 20),
    leanSource: truncateForAi(leanSourceCode.value, 12000),
    leanCheckStatus: leanCheckStatus.value,
    leanErrors: leanCheckErrors.value,
    leanWarnings: leanCheckWarnings.value,
    lastIntent: chatContextMemory.value.lastIntent,
    lastResponseMode: chatContextMemory.value.lastResponseMode,
    lastUserTask: chatContextMemory.value.lastUserTask,
    lastAiSummary: chatContextMemory.value.lastAiSummary,
    ...extraContext,
    chatResponseContract: 'Internal instruction: do not repeat this context to the user. If the user asks for code changes, return only JSON with type edit_proposal, target, title, summary, changes and full newSourceCode. If the user asks a question, return JSON with type message and message.'
  }
}

function classifyChatIntent(message) {
  const rawText = String(message || '').trim()
  const text = rawText.toLowerCase()
  const consultationHints = [
    '你认为', '是否', '是不是', '对吗', '可以吗', '合理吗', '为什么', '什么意思',
    '是什么', '怎么理解', '会不会', '有没有问题', '解释', '分析', '评价',
    'is this correct', 'what does this mean', 'why', 'how does this work', 'is this reasonable',
    'what is', 'explain'
  ]
  const explicitEditHints = [
    '帮我修改', '帮我加', '帮我实现', '帮我填充', '帮我补全', '帮我修复', '帮我重构',
    '帮我删除', '帮我替换', '帮我生成', '改成', '加上', '加一个', '删除', '替换',
    '应用到代码', '生成代码', '生成一个合约', '写一个函数', '写一个合约', '把这个模板填完整',
    '直接改', '直接修', '直接应用', '给我改', '给我修', '按这个改', '按这个修',
    '按你说的改', '按建议改', '按方案改', '落实到代码', '生成修改', '生成提案',
    '修一下', '改一下', '补一下', '完善一下', '优化一下', '处理一下',
    'modify', 'add', 'implement', 'fill', 'complete', 'refactor', 'fix', 'replace',
    'apply', 'generate code', 'write a function', 'update the code', 'make the change',
    'change it', 'patch it', 'fix it', 'do it'
  ]
  const actionQuestionHints = [
    '怎么改', '如何改', '怎么修', '如何修', '怎么处理', '如何处理', '怎么优化', '如何优化',
    '该怎么改', '应该怎么改', '可以怎么改', '要怎么改', '能不能改', '能不能修',
    'how should i change', 'how to change', 'how should i fix', 'how to fix',
    'what should i change', 'can you fix', 'can you update'
  ]
  const pureConsultationGuards = [
    '只解释', '不用改', '不要改', '先别改', '不需要修改', '不要生成修改',
    'only explain', 'do not change', "don't change", 'no code changes'
  ]
  const vagueEditHints = ['帮我弄一下', '弄一下']

  const hasExplicitEdit = explicitEditHints.some(keyword => text.includes(keyword.toLowerCase()))
  const hasActionQuestion = actionQuestionHints.some(keyword => text.includes(keyword.toLowerCase()))
  const hasConsultation = consultationHints.some(keyword => text.includes(keyword.toLowerCase()))
  const asksForSuggestionOnly = ['建议', '思路', '方案', '分析一下', '解释一下'].some(keyword => text.includes(keyword.toLowerCase()))
  const hasPureConsultationGuard = pureConsultationGuards.some(keyword => text.includes(keyword.toLowerCase()))
  const hasCurrentContext = Boolean(String(getCurrentSourceCode() || leanSourceCode.value || '').trim())

  if (hasPureConsultationGuard) return 'message'
  if (hasExplicitEdit || hasActionQuestion) return 'edit_proposal'
  if (hasConsultation && !hasExplicitEdit) return 'message'
  if (vagueEditHints.some(keyword => text.includes(keyword.toLowerCase()))) return hasCurrentContext ? 'edit_proposal' : 'clarify'
  if (/^(改|修|优化|完善|补全|实现|添加|删除|替换)/.test(rawText)) return 'edit_proposal'
  if (asksForSuggestionOnly) return 'message'
  return 'message'
}

function isContinuationMessage(message) {
  const text = String(message || '').trim().toLowerCase()
  if (!text || text.length > 16) return false
  return [
    '继续',
    '继续修改',
    '按刚刚的做',
    '按刚才的做',
    '继续刚才的',
    '可以',
    '就这样',
    '改吧',
    '修吧',
    '那就改',
    '就按这个改',
    '就按这个修',
    '按这个来',
    '生成提案',
    '给我提案',
    '修复',
    '应用这个思路',
    'continue',
    'go on',
    'do it',
    'apply it',
    'make it'
  ].some(keyword => text === keyword || text.includes(keyword))
}

function resolveChatRequestPlan(text, options = {}) {
  if (options.responseMode) {
    return {
      responseMode: options.responseMode,
      editIntent: options.responseMode === 'edit_proposal',
      editTarget: options.editTarget || (options.responseMode === 'edit_proposal' ? resolveEditTarget(text) : ''),
      intent: options.intent || ''
    }
  }

  if (isContinuationMessage(text) && chatContextMemory.value.lastIntent) {
    return {
      responseMode: chatContextMemory.value.lastResponseMode || 'message',
      editIntent: chatContextMemory.value.lastResponseMode === 'edit_proposal',
      editTarget: chatContextMemory.value.lastTarget || '',
      intent: chatContextMemory.value.lastIntent
    }
  }

  const responseMode = classifyChatIntent(text)
  return {
    responseMode,
    editIntent: responseMode === 'edit_proposal',
    editTarget: responseMode === 'edit_proposal' ? resolveEditTarget(text) : '',
    intent: responseMode === 'edit_proposal' ? 'code_edit' : responseMode
  }
}

function isCodeEditRequest(message) {
  return classifyChatIntent(message) === 'edit_proposal'
}

function resolveEditTarget(message) {
  const text = String(message || '').toLowerCase()
  const leanHints = ['lean', '证明', 'theorem', 'structure', 'rfl', 'trivial', '证明错误', '形式化']
  const solidityHints = ['合约', 'solidity', '函数', 'modifier', 'owner', 'event', '编译错误', '部署', '交易', 'setvalue', '模板']
  if (leanHints.some(keyword => text.includes(keyword.toLowerCase()))) return 'lean'
  if (solidityHints.some(keyword => text.includes(keyword.toLowerCase()))) return 'solidity'
  return activeLabMode.value === 'proof' ? 'lean' : 'solidity'
}

function buildAccessControlledStorageSource() {
  return `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract AccessControlledStorage {
    address public owner;
    uint256 private value;

    event ValueChanged(uint256 indexed oldValue, uint256 indexed newValue);
    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);

    modifier onlyOwner() {
        require(msg.sender == owner, "Not owner");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    function setValue(uint256 newValue) public onlyOwner {
        uint256 oldValue = value;
        value = newValue;
        emit ValueChanged(oldValue, newValue);
    }

    function getValue() public view returns (uint256) {
        return value;
    }

    function resetValue() public onlyOwner {
        uint256 oldValue = value;
        value = 0;
        emit ValueChanged(oldValue, 0);
    }

    function transferOwnership(address newOwner) public onlyOwner {
        require(newOwner != address(0), "Invalid owner");
        address previousOwner = owner;
        owner = newOwner;
        emit OwnershipTransferred(previousOwner, newOwner);
    }
}
`
}

function buildLeanFallbackSource() {
  return `-- Abstract model generated as a proof draft.
structure StorageState where
  value : Nat
  owner : Nat

def setValue (s : StorageState) (x : Nat) : StorageState :=
  { s with value := x }

theorem setValue_correct (s : StorageState) (x : Nat) :
  (setValue s x).value = x := by
  rfl
`
}

function createFallbackEditProposal(message, target) {
  const resolvedTarget = target || resolveEditTarget(message)
  const isLean = resolvedTarget === 'lean'
  const newSourceCode = isLean ? buildLeanFallbackSource() : buildAccessControlledStorageSource()

  return {
    id: createProposalId(),
    target: resolvedTarget,
    title: isLean ? '更新 Lean 证明' : `更新 ${currentFileName.value}`,
    summary: isLean
      ? '为当前合约生成一个抽象 Lean 证明草稿。'
      : '将起步模板填充为一个带权限控制的基础存储合约。',
    changes: isLean
      ? ['定义抽象状态', '建模 setValue 状态转移', '添加正确性定理']
      : ['添加 owner 权限控制', '添加 value 读写与重置逻辑', '添加所有权和值变更事件'],
    warnings: [],
    oldSourceCode: isLean ? leanSourceCode.value : getCurrentSourceCode(),
    newSourceCode,
    status: 'pending',
    reviewOpen: false,
    createdAt: Date.now()
  }
}

function createProposalId() {
  return `proposal-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function stripJsonFence(text) {
  const trimmed = String(text || '').trim()
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i)
  return fenced ? fenced[1].trim() : trimmed
}

function tryParseJsonMessage(text) {
  const raw = stripJsonFence(text)
  if (!raw.startsWith('{')) return null
  try {
    return JSON.parse(raw)
  } catch (err) {
    return null
  }
}

function hasCjkText(value) {
  return /[\u4e00-\u9fa5]/.test(String(value || ''))
}

function preferChineseText(value, fallback) {
  const text = String(value || '').trim()
  if (!text) return fallback
  return hasCjkText(text) ? text : fallback
}

function normalizeEditProposal(candidate) {
  const source = candidate?.type === 'edit_proposal'
    ? candidate
    : candidate?.data?.type === 'edit_proposal'
      ? candidate.data
      : typeof candidate?.message === 'string'
        ? tryParseJsonMessage(candidate.message)
        : typeof candidate === 'string'
          ? tryParseJsonMessage(candidate)
          : null

  if (!source || source.type !== 'edit_proposal' || typeof source.newSourceCode !== 'string') {
    return null
  }

  const target = ['solidity', 'lean'].includes(source.target)
    ? source.target
    : activeLabMode.value === 'proof'
      ? 'lean'
      : 'solidity'
  const oldSourceCode = target === 'lean' ? leanSourceCode.value : getCurrentSourceCode()
  const fallbackTitle = target === 'lean' ? '更新 Lean 证明' : `更新 ${currentFileName.value}`
  const changes = Array.isArray(source.changes)
    ? source.changes.map(item => String(item)).filter(Boolean).slice(0, 5)
    : []

  const fallbackSummary = target === 'lean'
    ? '根据你的要求生成 Lean 证明修改提案。'
    : '根据你的要求生成 Solidity 代码修改提案。'
  const normalizedChanges = changes.length
    ? changes.map((item, index) => preferChineseText(item, `修改项 ${index + 1}`))
    : ['更新当前代码']

  return {
    id: createProposalId(),
    target,
    title: preferChineseText(source.title, fallbackTitle),
    summary: preferChineseText(source.summary, fallbackSummary),
    changes: normalizedChanges,
    warnings: Array.isArray(source.warnings) ? source.warnings.map(item => String(item)).filter(Boolean).slice(0, 3) : [],
    oldSourceCode,
    newSourceCode: source.newSourceCode,
    status: 'pending',
    reviewOpen: false,
    createdAt: Date.now()
  }
}

function normalizeAssistantMessage(payload, fallbackReply) {
  if (payload?.type === 'message' && typeof payload.message === 'string') return payload.message
  if (payload?.data?.type === 'message' && typeof payload.data.message === 'string') return payload.data.message

  const parsed = typeof fallbackReply === 'string' ? tryParseJsonMessage(fallbackReply) : null
  if (parsed?.type === 'message' && typeof parsed.message === 'string') return parsed.message
  return fallbackReply
}

function formatProposalStatus(proposal) {
  const labels = {
    pending: '待审核',
    applied: '已应用',
    discarded: '已放弃'
  }
  return labels[proposal?.status] || '待审核'
}

function toggleProposalReview(proposal) {
  if (!proposal) return
  proposal.reviewOpen = !proposal.reviewOpen
}

function buildProposalDiff(proposal) {
  const oldLines = String(proposal?.oldSourceCode || '').split('\n')
  const newLines = String(proposal?.newSourceCode || '').split('\n')
  let start = 0
  while (start < oldLines.length && start < newLines.length && oldLines[start] === newLines[start]) {
    start += 1
  }

  if (start === oldLines.length && start === newLines.length) {
    return [{ type: 'context', text: '  无变化' }]
  }

  let oldEnd = oldLines.length - 1
  let newEnd = newLines.length - 1
  while (oldEnd >= start && newEnd >= start && oldLines[oldEnd] === newLines[newEnd]) {
    oldEnd -= 1
    newEnd -= 1
  }

  const beforeStart = Math.max(0, start - 3)
  const afterEnd = Math.min(oldLines.length - 1, oldEnd + 3)
  const lines = []

  if (beforeStart > 0) lines.push({ type: 'context', text: '  ...' })
  oldLines.slice(beforeStart, start).forEach(line => {
    lines.push({ type: 'context', text: `  ${line}` })
  })
  oldLines.slice(start, oldEnd + 1).forEach(line => {
    lines.push({ type: 'remove', text: `- ${line}` })
  })
  newLines.slice(start, newEnd + 1).forEach(line => {
    lines.push({ type: 'add', text: `+ ${line}` })
  })
  oldLines.slice(oldEnd + 1, afterEnd + 1).forEach(line => {
    lines.push({ type: 'context', text: `  ${line}` })
  })
  if (afterEnd < oldLines.length - 1) lines.push({ type: 'context', text: '  ...' })

  return lines.length > 90
    ? [...lines.slice(0, 89), { type: 'context', text: '  ...' }]
    : lines
}

function applyEditProposal(proposal) {
  if (!proposal || proposal.status !== 'pending') return

  if (proposal.target === 'lean') {
    leanSourceCode.value = proposal.newSourceCode
    handleLeanSourceInput()
    proposal.status = 'applied'
    proposal.reviewOpen = false
    chatContextMemory.value.lastProposal = proposal
    chatContextMemory.value.lastAiSummary = '已应用 Lean 修改提案'
    chatContextMemory.value.previousContext = {
      ...(chatContextMemory.value.previousContext || {}),
      leanSource: truncateForAi(proposal.newSourceCode, 12000)
    }
    appendOutputMessage({
      type: 'success',
      source: 'chat',
      message: '已应用 AI 修改提案'
    })
    showEditorMessage('已应用，建议检查证明')
    return
  }

  sourceCode.value = proposal.newSourceCode
  if (editorInstance.value && editorInstance.value.getValue() !== proposal.newSourceCode) {
    editorInstance.value.setValue(proposal.newSourceCode)
  } else {
    markSourceDirty()
  }

  if (!isEditorPreviewMode()) {
    const file = activeWorkspaceFile()
    if (file && !file.readonly) {
      file.content = proposal.newSourceCode
      persistWorkspace()
    }
  }

  proposal.status = 'applied'
  proposal.reviewOpen = false
  chatContextMemory.value.lastProposal = proposal
  chatContextMemory.value.lastAiSummary = `已应用 ${currentFileName.value} 修改提案`
  chatContextMemory.value.previousContext = {
    ...(chatContextMemory.value.previousContext || {}),
    soliditySource: truncateForAi(proposal.newSourceCode, 12000)
  }
  appendOutputMessage({
    type: 'success',
    source: 'chat',
    message: '已应用 AI 修改提案'
  })
  showEditorMessage('已应用，建议重新编译')
}

function discardEditProposal(proposal) {
  if (!proposal || proposal.status !== 'pending') return
  proposal.status = 'discarded'
  proposal.reviewOpen = false
  chatContextMemory.value.lastProposal = proposal
}

function syncChatContextBeforeSend() {
  if (activeLabMode.value !== 'edit') return
  const latestSource = getCurrentSourceCode()
  sourceCode.value = latestSource
  if (!isEditorPreviewMode()) {
    const file = activeWorkspaceFile()
    if (file && !file.readonly) {
      file.content = latestSource
      persistWorkspace()
    }
  }
}

async function sendChatMessage(message = chatInput.value, extraContext = {}, options = {}) {
  const text = String(message || '').trim()
  if (!text || isChatLoading.value) return false

  syncChatContextBeforeSend()
  const requestPlan = resolveChatRequestPlan(text, options)
  const responseMode = requestPlan.responseMode
  const editIntent = requestPlan.editIntent
  const editTarget = requestPlan.editTarget
  const previousContext = isContinuationMessage(text) && chatContextMemory.value.previousContext
    ? chatContextMemory.value.previousContext
    : {}
  const mergedExtraContext = {
    ...previousContext,
    ...extraContext,
    lastIntent: requestPlan.intent || chatContextMemory.value.lastIntent,
    lastResponseMode: responseMode
  }
  chatError.value = ''
  chatInput.value = ''
  if (!options.hiddenUserMessage) {
    appendChatMessage('user', text, {
      requestExtraContext: extraContext,
      requestOptions: options
    })
  }
  const assistantLocalId = options.assistantLocalId || `assistant-loading-${Date.now()}-${Math.random().toString(16).slice(2)}`
  replaceChatMessage(assistantLocalId, {
    localId: assistantLocalId,
    role: 'assistant',
    content: '正在分析...',
    loading: true,
    createdAt: Date.now()
  })
  isChatLoading.value = true
  persistSelectedModel()

  try {
    const result = await aiApi.chat({
      model: selectedModel.value,
      message: text,
      context: buildChatContext(mergedExtraContext),
      responseFormat: responseMode,
      responseMode,
      language: 'zh-CN',
      editIntent,
      editTarget
    })
    const payload = result?.data || result
    const reply = payload?.message || result?.message || 'AI 服务暂时不可用'
    const modelUsed = payload?.modelUsed || result?.modelUsed || selectedModel.value

    if (result?.status === false || payload?.success === false) {
      chatError.value = reply
      replaceChatMessage(assistantLocalId, {
        localId: assistantLocalId,
        role: 'assistant',
        content: reply || 'AI 请求失败，请重试',
        modelUsed,
        createdAt: Date.now()
      })
      appendOutputMessage({
        type: 'error',
        source: 'chat',
        message: `AI 请求失败：${reply}`
      })
      return false
    }

    const proposal = normalizeEditProposal(payload) || normalizeEditProposal(reply)
    appendOutputMessage({
      type: 'info',
      source: 'chat',
      message: `请求模型：${modelUsed}`
    })
    if (proposal) {
      replaceChatMessage(assistantLocalId, {
        localId: assistantLocalId,
        role: 'assistant',
        content: proposal.summary,
        type: 'edit_proposal',
        proposal,
        modelUsed,
        createdAt: Date.now()
      })
      chatContextMemory.value = {
        lastIntent: requestPlan.intent || (extraContext.source === 'precheck-report' ? 'fix_precheck_issues' : 'code_edit'),
        lastResponseMode: 'edit_proposal',
        lastTarget: proposal.target,
        lastUserTask: text,
        lastAiSummary: proposal.summary,
        lastProposal: proposal,
        previousContext: mergedExtraContext
      }
      return true
    }

    if (editIntent) {
      replaceChatMessage(assistantLocalId, {
        localId: assistantLocalId,
        role: 'assistant',
        content: '未生成可应用代码。请重试或补充具体修改目标。',
        modelUsed,
        createdAt: Date.now()
      })
      appendOutputMessage({
        type: 'warning',
        source: 'chat',
        message: 'AI 未返回可应用修改提案。'
      })
      return false
    }

    const parsed = typeof reply === 'string' ? tryParseJsonMessage(reply) : null
    const assistantReply = normalizeAssistantMessage(payload, reply)
    if (parsed && assistantReply === reply) {
      appendOutputMessage({
        type: 'warning',
        source: 'chat',
        message: 'AI response was shown as plain message'
      })
    }
    replaceChatMessage(assistantLocalId, {
      localId: assistantLocalId,
      role: 'assistant',
      content: assistantReply,
      modelUsed,
      createdAt: Date.now()
    })
    chatContextMemory.value = {
      ...chatContextMemory.value,
      lastIntent: requestPlan.intent || 'message',
      lastResponseMode: 'message',
      lastTarget: editTarget,
      lastUserTask: text,
      lastAiSummary: assistantReply,
      previousContext: mergedExtraContext
    }
    return true
  } catch (err) {
    const messageText = typeof err === 'string' ? err : (err?.message || 'AI 服务暂时不可用')
    chatError.value = messageText
    replaceChatMessage(assistantLocalId, {
      localId: assistantLocalId,
      role: 'assistant',
      content: messageText,
      modelUsed: selectedModel.value,
      createdAt: Date.now()
    })
    appendOutputMessage({
      type: 'error',
      source: 'chat',
      message: `AI 请求失败：${messageText}`
    })
    return false
  } finally {
    isChatLoading.value = false
    scrollChatToBottom()
  }
}

async function copyChatMessage(message) {
  const content = String(message?.content || '')
  if (!content.trim()) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(content)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = content
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    copiedChatMessageId.value = message.localId || message.createdAt || ''
    window.setTimeout(() => {
      if (copiedChatMessageId.value === (message.localId || message.createdAt)) copiedChatMessageId.value = ''
    }, 1400)
  } catch {
    chatError.value = '复制失败，请重试'
  }
}

async function regenerateChatMessage(message) {
  if (isChatLoading.value) return
  const assistantId = message.localId || message.createdAt
  const assistantIndex = chatMessages.value.findIndex(item => (item.localId || item.createdAt) === assistantId)
  if (assistantIndex <= 0) return
  let userMessage = null
  for (let index = assistantIndex - 1; index >= 0; index -= 1) {
    if (chatMessages.value[index]?.role === 'user') {
      userMessage = chatMessages.value[index]
      break
    }
  }
  if (!userMessage?.content) return
  await sendChatMessage(
    userMessage.content,
    userMessage.requestExtraContext || {},
    {
      ...(userMessage.requestOptions || {}),
      hiddenUserMessage: true,
      assistantLocalId: assistantId
    }
  )
}

function sendQuickChat(type) {
  if (type === 'draft') {
    sendChatMessage('请根据当前 Solidity 合约，生成一个 Lean4 证明草稿。不要声称已经直接证明 Solidity 语义，只需要抽象关键状态和函数性质。')
    return
  }

  if (type === 'explain') {
    if (!leanCheckErrors.value.length) {
      appendChatMessage('system', '当前没有 Lean 检查错误。')
      return
    }
    sendChatMessage('请解释当前 Lean 检查错误，并给出修改建议。')
    return
  }

  if (type === 'properties') {
    sendChatMessage('请根据当前 Solidity 合约，列出适合用 Lean4 表达的关键性质。')
  }
}

async function checkLeanProof() {
  if (isCheckingLean.value) return
  openPanelTab(PANEL_TABS.PROOF)
  appendOutputMessage({
    type: 'info',
    source: 'lean',
    message: '开始检查 Lean 证明'
  })

  const code = String(leanSourceCode.value || '')
  leanServiceError.value = ''
  leanCheckMessage.value = ''
  leanCheckErrors.value = []
  leanCheckWarnings.value = []
  leanCheckElapsedMs.value = null

  if (!code.trim()) {
    leanCheckStatus.value = 'failed'
    leanCheckErrors.value = [{
      line: null,
      column: null,
      severity: 'error',
      message: 'Lean 源码不能为空。'
    }]
    appendOutputMessage({
      type: 'error',
      source: 'lean',
      message: '证明检查失败：Lean 源码不能为空。'
    })
    return
  }

  isCheckingLean.value = true
  leanCheckStatus.value = 'checking'
  leanAbortController?.abort()
  leanAbortController = new AbortController()

  try {
    const response = await fetch(LEAN_CHECK_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sourceCode: code,
        fileName: 'Proof.lean'
      }),
      signal: leanAbortController.signal
    })

    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('application/json')) {
      throw new Error('Lean 检查服务返回异常')
    }

    const result = await response.json()
    leanCheckMessage.value = result.message || ''
    leanCheckElapsedMs.value = typeof result.elapsedMs === 'number' ? result.elapsedMs : null
    leanCheckErrors.value = Array.isArray(result.errors) ? result.errors : []
    leanCheckWarnings.value = Array.isArray(result.warnings) ? result.warnings : []

    if (!response.ok || result.success === false) {
      leanCheckStatus.value = 'failed'
      if (result.errorType === 'LEAN_NOT_FOUND') {
        leanServiceError.value = result.message || '未检测到 Lean 命令，请先安装 Lean4 或配置 LEAN_BIN。'
      }
      if (!leanCheckErrors.value.length && !leanServiceError.value) {
        leanCheckErrors.value = [{
          line: null,
          column: null,
          severity: 'error',
          message: result.message || 'Lean proof check failed.'
        }]
      }
      const firstError = leanCheckErrors.value[0]
      appendOutputMessage({
        type: 'error',
        source: 'lean',
        message: `证明检查失败：${firstError ? `${formatLeanDiagnosticLocation(firstError)}, ${firstError.message}` : (leanServiceError.value || result.message || 'Lean proof check failed.')}`
      })
      leanCheckWarnings.value.forEach(item => {
        appendOutputMessage({
          type: 'warning',
          source: 'lean',
          message: `Warning: ${formatLeanDiagnosticLocation(item)}, ${item.message}`
        })
      })
      return
    }

    leanCheckStatus.value = 'passed'
    appendOutputMessage({
      type: 'success',
      source: 'lean',
      message: `证明检查通过${leanCheckElapsedMs.value !== null ? `，Elapsed ${leanCheckElapsedMs.value} ms` : ''}`
    })
    leanCheckWarnings.value.forEach(item => {
      appendOutputMessage({
        type: 'warning',
        source: 'lean',
        message: `Warning: ${formatLeanDiagnosticLocation(item)}, ${item.message}`
      })
    })
  } catch (err) {
    leanCheckStatus.value = 'failed'
    const message = err.name === 'AbortError'
      ? 'Lean 检查请求已取消或超时。'
      : 'Lean 检查服务未启动，请先启动 lean-runtime-service。'
    leanServiceError.value = message
    leanCheckMessage.value = ''
    leanCheckErrors.value = []
    leanCheckWarnings.value = []
    appendOutputMessage({
      type: 'error',
      source: 'lean',
      message
    })
  } finally {
    isCheckingLean.value = false
    leanAbortController = null
  }
}

function toggleDock() {
  dockCollapsed.value = !dockCollapsed.value
  persistLayout()
  nextTick(scheduleEditorLayout)
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function persistLayout() {
  localStorage.setItem(LAB_LAYOUT_KEY, JSON.stringify({
    workspaceWidth: workspaceWidth.value,
    resultPanelWidth: resultPanelWidth.value,
    dockHeight: dockHeight.value
  }))
}

function restoreLayout() {
  try {
    const raw = localStorage.getItem(LAB_LAYOUT_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    workspaceWidth.value = clamp(Number(saved.workspaceWidth) || 240, 220, 360)
    resultPanelWidth.value = clamp(Number(saved.resultPanelWidth) || 360, 260, 460)
    dockHeight.value = clamp(Number(saved.dockHeight) || 280, 180, 360)
  } catch (err) {
    localStorage.removeItem(LAB_LAYOUT_KEY)
  }
}

function startResize(type, event) {
  event.preventDefault()
  resizeState.value = {
    type,
    startX: event.clientX,
    startY: event.clientY,
    workspaceWidth: workspaceWidth.value,
    resultPanelWidth: resultPanelWidth.value,
    dockHeight: dockHeight.value
  }
  document.body.classList.add('contract-lab-resizing')
  window.addEventListener('mousemove', handleResizeMove)
  window.addEventListener('mouseup', stopResize)
}

function handleResizeMove(event) {
  const state = resizeState.value
  if (!state) return

  if (state.type === 'left') {
    workspaceWidth.value = clamp(state.workspaceWidth + event.clientX - state.startX, 220, 360)
  } else if (state.type === 'right') {
    resultPanelWidth.value = clamp(state.resultPanelWidth - (event.clientX - state.startX), 260, 460)
  } else if (state.type === 'dock') {
    dockHeight.value = clamp(state.dockHeight - (event.clientY - state.startY), 180, 360)
  }

  scheduleEditorLayout()
}

function stopResize() {
  resizeState.value = null
  document.body.classList.remove('contract-lab-resizing')
  window.removeEventListener('mousemove', handleResizeMove)
  window.removeEventListener('mouseup', stopResize)
  persistLayout()
  scheduleEditorLayout()
}

function resetConstructorArgs() {
  constructorArgValues.value = constructorInputs.value.map((input, index) => (
    constructorArgValues.value[index] ?? ''
  ))
}

function markSourceDirty() {
  if (isCompiling.value) return
  if (compileStatus.value === 'success' || compileStatus.value === 'failed') {
    resetCompileResult({ markModified: true })
  }
}

function clearCompileMarkers() {
  const monaco = monacoApi.value
  const model = editorInstance.value?.getModel()
  if (monaco && model) {
    monaco.editor.setModelMarkers(model, MARKER_OWNER, [])
  }
}

function applyCompileMarkers() {
  const monaco = monacoApi.value
  const model = editorInstance.value?.getModel()
  if (!monaco || !model) return

  const diagnostics = [
    ...compileErrors.value.map(item => ({ ...item, markerSeverity: monaco.MarkerSeverity.Error })),
    ...compileWarnings.value.map(item => ({ ...item, markerSeverity: monaco.MarkerSeverity.Warning }))
  ]

  const markers = diagnostics
    .filter(item => {
      const line = Number(item.line)
      return Number.isFinite(line) && line >= 1 && line <= model.getLineCount()
    })
    .map(item => {
      const line = Number(item.line)
      const column = Number(item.column) || 1
      const lineLength = model.getLineLength(line) || column

      return {
        severity: item.markerSeverity,
        message: item.message || item.formattedMessage || 'Solidity compile diagnostic',
        startLineNumber: line,
        startColumn: column,
        endLineNumber: line,
        endColumn: Math.min(Math.max(column + 1, 2), lineLength + 1)
      }
    })

  monaco.editor.setModelMarkers(model, MARKER_OWNER, markers)
}

function registerSolidityLanguage() {
  const monaco = monacoApi.value
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

async function loadMonaco() {
  if (!editorWorkerRegistered && !window.MonacoEnvironment) {
    const { default: EditorWorker } = await import('monaco-editor/esm/vs/editor/editor.worker?worker')
    window.MonacoEnvironment = {
      getWorker() {
        return new EditorWorker()
      }
    }
    editorWorkerRegistered = true
  }

  if (!monacoApi.value) {
    if (!monacoLoadPromise) {
      monacoLoadPromise = import('monaco-editor/esm/vs/editor/editor.api')
    }
    monacoApi.value = markRaw(await monacoLoadPromise)
  }

  return monacoApi.value
}

async function createEditor() {
  const bootId = editorBootId
  if (!editorContainer.value || editorInstance.value) return

  editorLoading.value = true
  const monaco = await loadMonaco()
  if (bootId !== editorBootId || editorInstance.value) return

  registerSolidityLanguage()
  editorInstance.value = markRaw(monaco.editor.create(editorContainer.value, {
    value: sourceCode.value,
    language: 'solidity',
    theme: 'contractLabLight',
    automaticLayout: false,
    minimap: { enabled: false },
    fontFamily: "'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace",
    fontSize: 13,
    lineHeight: 22,
    padding: { top: 14, bottom: 14 },
    scrollBeyondLastLine: false,
    wordWrap: 'off',
    tabSize: 4,
    folding: true,
    fontLigatures: false,
    renderWhitespace: 'none',
    renderLineHighlight: 'line',
    smoothScrolling: false,
    overviewRulerBorder: false,
    hideCursorInOverviewRuler: true,
    fixedOverflowWidgets: true
  }))

  contentChangeDisposable = editorInstance.value.onDidChangeModelContent(() => {
    sourceCode.value = editorInstance.value.getValue()
    markSourceDirty()
  })

  editorLoading.value = false
  editorReady.value = true
  applyCompileMarkers()
  scheduleEditorLayout()
}

function scheduleEditorLayout() {
  if (!editorInstance.value) return

  window.cancelAnimationFrame(layoutFrame)
  window.clearTimeout(layoutTimer)
  layoutFrame = window.requestAnimationFrame(() => {
    layoutTimer = window.setTimeout(() => {
      editorInstance.value?.layout()
    }, 80)
  })
}

function applyTemplate(template) {
  createFileFromTemplate({
    name: template.label,
    content: template.code,
    readonly: true,
    templateKey: template.key
  })
}

function showPendingMessage(action) {
  editorMessage.value = `${action}功能将在后续接入`
  window.clearTimeout(pendingMessageTimer)
  pendingMessageTimer = window.setTimeout(() => {
    editorMessage.value = ''
  }, 1800)
}

function showEditorMessage(message) {
  editorMessage.value = message
  window.clearTimeout(pendingMessageTimer)
  pendingMessageTimer = window.setTimeout(() => {
    editorMessage.value = ''
  }, 1800)
}

async function sendToContractCheck() {
  activeLabMode.value = 'edit'
  const code = getCurrentSourceCode()
  sourceCode.value = code
  saveActiveFileContent()

  if (!code.trim()) {
    showEditorMessage('请先输入 Solidity 源码')
    return
  }

  try {
    sessionStorage.setItem(PRECHECK_TRANSFER_KEY, JSON.stringify({
      source: 'contract-lab',
      fileName: currentFileName.value,
      sourceCode: code,
      createdAt: Date.now()
    }))
  } catch (err) {
    showEditorMessage('无法暂存源码，请检查浏览器存储权限')
    return
  }

  if (document.fullscreenElement) {
    await document.exitFullscreen().catch(() => {})
  }

  router.push('/contract-check')
}

function buildImportedFileName(payload) {
  const base = String(payload?.contractName || payload?.fileName || '')
    .replace(/\.(sol|txt)$/i, '')
    .replace(/[^\w\u4e00-\u9fa5-]/g, '')
  return `${base || 'Contract'}.sol`
}

function cancelLabConfirm() {
  if (labConfirm.value.type === 'import-contract-detail') {
    sessionStorage.removeItem(DETAIL_TO_LAB_KEY)
  }
  labConfirm.value = { open: false, type: '', title: '', message: '', payload: null }
}

function confirmLabAction() {
  const pending = labConfirm.value
  labConfirm.value = { open: false, type: '', title: '', message: '', payload: null }
  if (pending.type === 'import-contract-detail') {
    importContractDetailPayload(pending.payload)
  }
}

function isDefaultTemplateContent(code) {
  const activeCode = templates.find(item => item.key === activeTemplate.value)?.code
  return Boolean(activeCode && code === activeCode)
}

function consumeContractDetailSource() {
  let payload = null

  try {
    const raw = sessionStorage.getItem(DETAIL_TO_LAB_KEY)
    if (!raw) return
    payload = JSON.parse(raw)
  } catch (err) {
    sessionStorage.removeItem(DETAIL_TO_LAB_KEY)
    return
  }

  if (payload?.source !== 'contract-detail' || typeof payload.sourceCode !== 'string' || !payload.sourceCode.trim()) {
    sessionStorage.removeItem(DETAIL_TO_LAB_KEY)
    return
  }

  const currentCode = getCurrentSourceCode()
  const shouldConfirm = currentCode.trim() && !isDefaultTemplateContent(currentCode)
  if (shouldConfirm) {
    labConfirm.value = {
      open: true,
      type: 'import-contract-detail',
      title: '替换当前编辑内容？',
      message: '当前编辑内容将被合约库源码替换。继续前会保存当前文件内容。',
      payload
    }
    return
  }

  importContractDetailPayload(payload)
}

function importContractDetailPayload(payload) {
  const importedCode = payload.sourceCode
  sessionStorage.removeItem(DETAIL_TO_LAB_KEY)
  saveActiveFileContent()
  const fileName = buildImportedFileName(payload)
  const existing = workspaceFiles.value.find(file => file.name === fileName && !file.readonly)
  const targetFile = existing || {
    id: `file-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    folderId: 'contracts',
    name: uniqueFileName(fileName, 'contracts'),
    content: importedCode,
    meta: {}
  }
  targetFile.content = importedCode
  targetFile.meta = {
    sourceLabel: 'Contract Library',
    contractName: payload.contractName || '',
    contractAddress: payload.contractAddress || '',
    abi: payload.abi || ''
  }
  if (!existing) {
    workspaceFiles.value = [...workspaceFiles.value, targetFile]
  }
  activeFileId.value = targetFile.id
  activeTemplate.value = ''
  currentFileName.value = targetFile.name
  importedSourceMeta.value = targetFile.meta
  sourceCode.value = importedCode

  if (editorInstance.value && editorInstance.value.getValue() !== importedCode) {
    editorInstance.value.setValue(importedCode)
  }

  resetCompileResult()
  appendOutputMessage({ text: '[import] Imported source from contract library.', muted: true })
  showEditorMessage('已从合约库导入源码')
  persistWorkspace()
  nextTick(scheduleEditorLayout)
}

function shortenHash(value) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 10)}...${value.slice(-6)}` : value
}

function functionKey(fn) {
  const inputTypes = (fn.inputs || []).map(input => input.type).join(',')
  return `${fn.name}(${inputTypes})`
}

function getFunctionInputKey(fn, index) {
  return `${functionKey(fn)}::arg::${index}`
}

function getFunctionValueKey(fn) {
  return `${functionKey(fn)}::value`
}

function formatFunctionSignature(fn) {
  const params = (fn.inputs || [])
    .map(input => `${input.type}${input.name ? ` ${input.name}` : ''}`)
    .join(', ')
  return `${fn.name}(${params})`
}

function getInputPlaceholder(input) {
  if (input.type.endsWith('[]')) return '请输入 JSON 数组，例如 [1,2,3]'
  if (input.type.startsWith('uint') || input.type.startsWith('int')) return `请输入 ${input.type} 数值，例如 123`
  if (input.type === 'bool') return '请输入 true 或 false'
  if (input.type === 'address') return '请输入地址，例如 0x...'
  if (input.type.startsWith('bytes')) return '请输入 0x 开头的字节值'
  if (input.type === 'string') return '请输入文本'
  return `请输入 ${input.type}`
}

function getEmptyArgMessage(input, label) {
  if (input.type.endsWith('[]')) return `${label} 不能为空，请输入 JSON 数组，例如 [1,2,3]`
  if (input.type.startsWith('uint') || input.type.startsWith('int')) return `${label} 不能为空，请输入一个 ${input.type} 数值`
  if (input.type === 'bool') return `${label} 不能为空，请输入 true 或 false`
  if (input.type === 'address') return `${label} 不能为空，请输入一个 address 地址`
  if (input.type.startsWith('bytes')) return `${label} 不能为空，请输入 0x 开头的字节值`
  if (input.type === 'string') return `${label} 不能为空，请输入文本`
  return `${label} 不能为空，请输入 ${input.type}`
}

function getFunctionInput(fn, index) {
  return functionInputs.value[getFunctionInputKey(fn, index)] ?? ''
}

function getFunctionArgError(fn, index) {
  return functionArgErrors.value[getFunctionInputKey(fn, index)] || ''
}

function updateFunctionArg(fn, index, value) {
  const key = getFunctionInputKey(fn, index)
  functionInputs.value = {
    ...functionInputs.value,
    [key]: value
  }

  if (functionArgErrors.value[key]) {
    const { [key]: _removedError, ...rest } = functionArgErrors.value
    functionArgErrors.value = rest
  }

  const previousCallError = callError.value
  if (previousCallError) {
    callError.value = ''
  }

  if (functionCallStatus.value === 'failed') {
    functionCallStatus.value = 'idle'
  }

  if (lastCallResult.value?.type === 'failed') {
    lastCallResult.value = null
  }

  callHistory.value = callHistory.value.filter(item => (
    item.key !== functionKey(fn) || item.status !== 'failed'
  ))

  if (previousCallError && outputMessages.value.some(line => line.text === previousCallError || line.text?.includes('不能为空'))) {
    setOutputMessages([])
  }
}

function getFunctionValue(fn) {
  return functionInputs.value[getFunctionValueKey(fn)] ?? '0'
}

function updateFunctionValue(fn, value) {
  functionInputs.value = {
    ...functionInputs.value,
    [getFunctionValueKey(fn)]: value
  }
}

function isFunctionCalling(fn) {
  return isCallingFunction.value && callingFunctionKey.value === functionKey(fn)
}

function selectInteractionFunction(fn) {
  selectedFunctionKey.value = functionKey(fn)
}

function isReadFunction(fn) {
  return fn.stateMutability === 'view' || fn.stateMutability === 'pure'
}

function getFunctionResult(fn) {
  const key = functionKey(fn)
  const result = callHistory.value.find(item => item.key === key)
  if (!result) return ''
  if (result.status === 'failed') return result.message
  if (result.type === 'call') return result.returnValue
  return result.transactionHash ? `Tx ${shortenHash(result.transactionHash)}` : ''
}

function formatRuntimeValue(value) {
  if (value === undefined) return 'empty'
  if (value === null) return 'null'
  if (typeof value === 'bigint') return value.toString()
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, (_key, currentValue) => (
        typeof currentValue === 'bigint' ? currentValue.toString() : currentValue
      ))
    } catch (err) {
      return String(value)
    }
  }
  return String(value)
}

function normalizeEventArgs(args) {
  if (!args || typeof args !== 'object') return {}
  return Object.fromEntries(
    Object.entries(args).filter(([key]) => Number.isNaN(Number(key)))
  )
}

function getReadResult(fn) {
  return readResults.value[functionKey(fn)] || null
}

function getFunctionExecutionRows(fn) {
  const key = functionKey(fn)
  const result = callHistory.value.find(item => item.key === key && item.status === 'success')
  const readResult = isReadFunction(fn) ? getReadResult(fn) : null
  const source = readResult || result
  if (!source) return []

  if (isReadFunction(fn)) {
    return [
      { label: 'Type', type: 'call', value: 'Read Call' },
      { label: 'Function', type: 'name', value: fn.name },
      { label: 'Return', type: 'value', value: formatRuntimeValue(source.returnValue) }
    ]
  }

  return [
    { label: 'Type', type: 'tx', value: 'Transaction' },
    { label: 'Function', type: 'name', value: fn.name },
    { label: 'Status', type: 'state', value: 'Success' },
    { label: 'Gas Used', type: 'gas', value: source.gasUsed || '-' },
    { label: 'Block', type: 'block', value: source.blockNumber || '-' },
    { label: 'Events', type: 'logs', value: `${Array.isArray(source.events) ? source.events.length : 0} emitted` },
    ...(source.transactionHash ? [{ label: 'Tx', type: 'hash', value: shortenHash(source.transactionHash) }] : [])
  ]
}

function getFunctionStateUpdates(fn) {
  if (isReadFunction(fn)) return []
  const result = callHistory.value.find(item => item.key === functionKey(fn) && item.status === 'success')
  return Array.isArray(result?.stateUpdates) ? result.stateUpdates : []
}

function formatStateUpdate(update) {
  if (update.previousValue === undefined) return formatRuntimeValue(update.currentValue)
  return `${formatRuntimeValue(update.previousValue)} -> ${formatRuntimeValue(update.currentValue)}`
}

function isEmptyArg(value) {
  return value === undefined || value === null || String(value).trim() === ''
}

function setFunctionArgError(key, message) {
  functionArgErrors.value = {
    ...functionArgErrors.value,
    [key]: message
  }
}

function clearFunctionArgErrors(fn) {
  const prefix = `${functionKey(fn)}::arg::`
  functionArgErrors.value = Object.fromEntries(
    Object.entries(functionArgErrors.value).filter(([key]) => !key.startsWith(prefix))
  )
}

function parseFunctionArg(input, rawValue, key, index) {
  const label = input.name || `参数 ${index + 1}`

  if (isEmptyArg(rawValue)) {
    const message = getEmptyArgMessage(input, label)
    setFunctionArgError(key, message)
    throw new Error(message)
  }

  const value = String(rawValue).trim()

  if (input.type.endsWith('[]')) {
    try {
      const parsed = JSON.parse(value)
      if (!Array.isArray(parsed)) throw new Error('not array')
      return parsed
    } catch (err) {
      const message = `${label} 需要 JSON 数组格式`
      setFunctionArgError(key, message)
      throw new Error(message)
    }
  }

  if (/^u?int(\d+)?$/.test(input.type)) {
    if (!/^-?\d+$/.test(value)) {
      const message = `${label} 必须是整数`
      setFunctionArgError(key, message)
      throw new Error(message)
    }
    if (input.type.startsWith('uint') && value.startsWith('-')) {
      const message = `${label} 不能为负数`
      setFunctionArgError(key, message)
      throw new Error(message)
    }
    return value
  }

  if (input.type === 'bool') {
    if (typeof rawValue === 'boolean') return rawValue
    if (value !== 'true' && value !== 'false') {
      const message = `${label} 只能是 true 或 false`
      setFunctionArgError(key, message)
      throw new Error(message)
    }
    return value === 'true'
  }

  if (input.type === 'address') {
    if (!/^0x[a-fA-F0-9]{40}$/.test(value)) {
      const message = `${label} 地址格式不正确`
      setFunctionArgError(key, message)
      throw new Error(message)
    }
    return value
  }

  if (input.type === 'bytes32') {
    if (!/^0x[a-fA-F0-9]{64}$/.test(value)) {
      const message = `${label} 需要 32 字节 hex`
      setFunctionArgError(key, message)
      throw new Error(message)
    }
    return value
  }

  if (input.type.startsWith('bytes')) {
    if (!/^0x[a-fA-F0-9]*$/.test(value)) {
      const message = `${label} 需要 hex 字符串`
      setFunctionArgError(key, message)
      throw new Error(message)
    }
    return value
  }

  return value
}

async function copyValue(value, field) {
  if (!value) return

  try {
    await navigator.clipboard.writeText(value)
    copiedField.value = field
    window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => {
      copiedField.value = ''
    }, 1400)
  } catch (err) {
    showEditorMessage('复制失败，请手动选择复制')
  }
}

function handleFallbackInput() {
  markSourceDirty()
}

async function compileContract() {
  if (isCompiling.value) return
  activeLabMode.value = 'edit'
  openPanelTab(PANEL_TABS.RESULT)

  const code = getCurrentSourceCode()
  sourceCode.value = code
  saveActiveFileContent()
  appendOutputMessage({
    type: 'info',
    source: 'compile',
    message: `开始编译 ${currentFileName.value}`
  })
  clearCompileMarkers()
  compileErrors.value = []
  compileWarnings.value = []
  resetDeployResult()

  if (!code.trim()) {
    compileStatus.value = 'failed'
    compiledContracts.value = []
    selectedContractName.value = ''
    compileDurationMs.value = null
    detectedPragma.value = null
    bundledCompilerVersion.value = ''
    selectedCompilerVersion.value = ''
    versionMismatch.value = false
    targetCompilerRelease.value = ''
    compilerMode.value = compilerModeSelection.value === 'auto' ? 'auto' : 'manual'
    compileErrors.value = [{
      severity: 'error',
      message: '源码不能为空',
      formattedMessage: '源码不能为空',
      line: null,
      column: null
    }]
    setOutputMessages([{ text: '源码不能为空', muted: false }])
    appendOutputMessage({
      type: 'error',
      source: 'compile',
      message: `编译失败：${currentFileName.value}`,
      detail: '源码不能为空'
    })
    return
  }

  isCompiling.value = true
  compileStatus.value = 'compiling'
  compiledContracts.value = []
  selectedContractName.value = ''
  compileDurationMs.value = null
  setOutputMessages([])

  compileAbortController?.abort()
  compileAbortController = new AbortController()
  const timeoutId = window.setTimeout(() => {
    compileAbortController?.abort()
  }, 15000)
  const startedAt = performance.now()

  try {
    const response = await fetch(COMPILE_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sourceCode: code,
        fileName: currentFileName.value,
        compilerVersion: compilerModeSelection.value
      }),
      signal: compileAbortController.signal
    })

    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('application/json')) {
      throw new Error('编译服务返回异常')
    }

    const result = await response.json()
    const durationMs = Math.round(performance.now() - startedAt)
    compileDurationMs.value = durationMs
    detectedPragma.value = result.detectedPragma || null
    bundledCompilerVersion.value = result.compilerVersion || ''
    selectedCompilerVersion.value = result.selectedCompilerVersion || result.compilerVersion || ''
    versionMismatch.value = Boolean(result.versionMismatch)
    targetCompilerRelease.value = result.targetCompilerRelease || ''
    compilerMode.value = result.compilerMode || (compilerModeSelection.value === 'auto' ? 'auto' : 'manual')
    compileErrors.value = Array.isArray(result.errors) ? result.errors : []
    compileWarnings.value = Array.isArray(result.warnings) ? result.warnings : []
    compiledContracts.value = Array.isArray(result.contracts) ? result.contracts : []
    selectedContractName.value = result.contractName
      || compiledContracts.value.find(contract => contract.bytecode && contract.bytecode !== '0x')?.contractName
      || compiledContracts.value[0]?.contractName
      || ''

    if (!response.ok || result.success === false) {
      compileStatus.value = 'failed'
      setOutputMessages([
        { text: versionMismatch.value ? `编译器版本不匹配，耗时 ${durationMs}ms` : `编译失败，耗时 ${durationMs}ms`, muted: false }
      ])
      const firstError = compileErrors.value[0]
      appendOutputMessage({
        type: 'error',
        source: 'compile',
        message: `编译失败：${currentFileName.value}`,
        detail: firstError ? `${formatDiagnosticLocation(firstError)}, ${firstError.message || firstError.formattedMessage}` : '请查看结果'
      })
    } else {
      compileStatus.value = 'success'
      resetConstructorArgs()
      setOutputMessages([])
      appendOutputMessage({
        type: 'success',
        source: 'compile',
        message: `编译成功：${compileTargetLabel.value}，${compileWarnings.value.length ? `${compileWarnings.value.length} warning` : 'No errors / No warnings'}`
      })
    }
    compileWarnings.value.forEach(item => {
      appendOutputMessage({
        type: 'warning',
        source: 'compile',
        message: `Warning: ${formatDiagnosticLocation(item)}, ${item.message || item.formattedMessage}`
      })
    })

    applyCompileMarkers()
  } catch (err) {
    let message = '编译服务未启动，请先启动 contract-runtime-service'
    if (err.name === 'AbortError') {
      message = '编译请求超时，请检查 contract-runtime-service'
    } else if (err.message === '编译服务返回异常') {
      message = '编译服务返回异常'
    }

    compileStatus.value = 'failed'
    compiledContracts.value = []
    selectedContractName.value = ''
    compileDurationMs.value = null
    detectedPragma.value = null
    bundledCompilerVersion.value = ''
    selectedCompilerVersion.value = ''
    versionMismatch.value = false
    targetCompilerRelease.value = ''
    compilerMode.value = compilerModeSelection.value === 'auto' ? 'auto' : 'manual'
    compileErrors.value = [{
      severity: 'error',
      message,
      formattedMessage: `${message}\n${err.message || ''}`.trim(),
      line: null,
      column: null
    }]
    compileWarnings.value = []
    setOutputMessages([
      { text: message, muted: false },
      { text: `Runtime API: ${COMPILE_ENDPOINT}`, muted: true }
    ])
    appendOutputMessage({
      type: 'error',
      source: 'compile',
      message: `编译失败：${currentFileName.value}`,
      detail: err.message ? `${message}\n${err.message}` : message
    })
    applyCompileMarkers()
  } finally {
    window.clearTimeout(timeoutId)
    isCompiling.value = false
    compileAbortController = null
  }
}

function getConstructorArgs() {
  return constructorInputs.value.map((input, index) => {
    const value = constructorArgValues.value[index]
    return value === undefined ? '' : value
  })
}

function normalizeRuntimeUserMessage(message) {
  const text = String(message || '')
  if (/invalid BigNumberish|string value|invalid value for value\.|cannot convert/i.test(text)) {
    return '参数格式不正确，请检查数值参数'
  }
  if (/missing argument|too few arguments|required argument/i.test(text)) {
    return '请填写所有必填参数'
  }
  if (/network|fetch|timeout|aborted/i.test(text)) {
    return '运行服务暂时不可用，请稍后重试'
  }
  return text || '操作失败，请重试'
}

function setDeployFailure(message, errorType = '') {
  const userMessage = normalizeRuntimeUserMessage(message)
  deployStatus.value = 'failed'
  deployError.value = errorType ? `${userMessage} (${errorType})` : userMessage
  setOutputMessages([
    { text: deployError.value, muted: false },
    { text: `Runtime API: ${DEPLOY_ENDPOINT}`, muted: true }
  ])
  appendOutputMessage({
    type: 'error',
    source: 'deploy',
    message: `部署失败：${deployError.value}`
  })
}

async function deployCurrentContract() {
  activeLabMode.value = 'edit'
  openPanelTab(PANEL_TABS.RESULT)
  appendOutputMessage({
    type: 'info',
    source: 'deploy',
    message: `开始部署 ${selectedContractName.value || 'Contract'}（来源：${currentFileName.value}）`
  })

  if (compileStatus.value === 'idle' || compileStatus.value === 'modified') {
    setDeployFailure('请先编译合约')
    return
  }

  if (compileStatus.value === 'failed') {
    setDeployFailure('请修复编译错误后再运行')
    return
  }

  if (!selectedContract.value || !Array.isArray(selectedContractAbi.value) || !hasDeployableBytecode.value) {
    setDeployFailure('缺少 ABI 或 Bytecode，无法部署')
    return
  }

  if (hasMissingConstructorArgs.value) {
    setDeployFailure('请填写构造函数参数')
    return
  }

  isDeploying.value = true
  deployStatus.value = 'deploying'
  deployError.value = ''
  runtimeSessionId.value = ''
  deployedContractAddress.value = ''
  deployer.value = ''
  deploymentTransactionHash.value = ''
  deploymentGasUsed.value = ''
  deploymentBlockNumber.value = ''
  runtimeNetwork.value = ''

  deployAbortController?.abort()
  deployAbortController = new AbortController()
  const timeoutId = window.setTimeout(() => {
    deployAbortController?.abort()
  }, 20000)

  try {
    const response = await fetch(DEPLOY_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        contractName: selectedContractName.value,
        abi: selectedContractAbi.value,
        bytecode: selectedContractBytecode.value,
        constructorArgs: getConstructorArgs()
      }),
      signal: deployAbortController.signal
    })

    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('application/json')) {
      throw new Error('运行服务返回异常')
    }

    const result = await response.json()
    if (!response.ok || result.success === false) {
      setDeployFailure(result.message || '部署失败', result.errorType)
      return
    }

    deployStatus.value = 'success'
    deployError.value = ''
    runtimeSessionId.value = result.sessionId || ''
    deployedContractAddress.value = result.contractAddress || ''
    deployer.value = result.deployer || ''
    deploymentTransactionHash.value = result.transactionHash || ''
    deploymentGasUsed.value = result.gasUsed || ''
    deploymentBlockNumber.value = result.blockNumber || ''
    runtimeNetwork.value = result.network || 'Local Sandbox'
    resetCallResult()
    openPanelTab(PANEL_TABS.INTERACTION)
    dockCollapsed.value = false
    appendOutputMessage({
      type: 'success',
      source: 'deploy',
      message: `部署成功：地址 ${shortenHash(deployedContractAddress.value)}，Gas Used ${deploymentGasUsed.value || '-'}`
    })
  } catch (err) {
    let message = '运行服务未启动，请先启动 contract-runtime-service'
    if (err.name === 'AbortError') {
      message = '部署请求超时，请检查 contract-runtime-service'
    } else if (err.message === '运行服务返回异常') {
      message = '运行服务返回异常'
    }

    setDeployFailure(message)
  } finally {
    window.clearTimeout(timeoutId)
    isDeploying.value = false
    deployAbortController = null
  }
}

function setFunctionFailure(fn, message, errorType = '') {
  const key = functionKey(fn)
  const userMessage = normalizeRuntimeUserMessage(message)
  const fullMessage = errorType ? `${userMessage} (${errorType})` : userMessage
  functionCallStatus.value = 'failed'
  callError.value = fullMessage
  lastCallResult.value = {
    type: 'failed',
    functionName: fn.name,
    message: fullMessage
  }
  callHistory.value = [
    {
      key,
      status: 'failed',
      message: fullMessage
    },
    ...callHistory.value.filter(item => item.key !== key)
  ]
  setOutputMessages([
    { text: fullMessage, muted: false },
    { text: `Runtime API: ${CALL_ENDPOINT}`, muted: true }
  ])
  appendOutputMessage({
    type: 'error',
    source: 'call',
    message: `调用失败：${fullMessage}`
  })
}

function buildFunctionArgs(fn) {
  clearFunctionArgErrors(fn)
  return (fn.inputs || []).map((input, index) => {
    const key = getFunctionInputKey(fn, index)
    return parseFunctionArg(input, functionInputs.value[key], key, index)
  })
}

async function requestRuntimeCall(payload, signal) {
  const response = await fetch(CALL_ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload),
    signal
  })

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    throw new Error('运行服务返回异常')
  }

  const result = await response.json()
  if (!response.ok || result.success === false) {
    const message = result.errorType === 'SESSION_NOT_FOUND'
      ? '当前运行会话不存在或已过期，请重新部署合约'
      : result.message || '函数调用失败'
    const error = new Error(message)
    error.errorType = result.errorType
    throw error
  }

  return result
}

function saveReadResult(fn, result) {
  const key = functionKey(fn)
  readResults.value = {
    ...readResults.value,
    [key]: {
      success: true,
      functionName: fn.name,
      returnValue: result.returnValue,
      updatedAt: new Date().toISOString()
    }
  }
}

function saveCallHistory(fn, result, extras = {}) {
  const key = functionKey(fn)
  callHistory.value = [
    {
      key,
      status: 'success',
      ...result,
      ...extras
    },
    ...callHistory.value.filter(item => item.key !== key)
  ]
}

function saveEventLogs(result) {
  if (!Array.isArray(result.events) || !result.events.length) return

  eventLogs.value = [
    ...result.events.map(event => ({
      ...event,
      functionName: result.functionName,
      transactionHash: event.transactionHash || result.transactionHash,
      blockNumber: event.blockNumber || result.blockNumber
    })),
    ...eventLogs.value
  ]
}

async function refreshReadFunctionsAfterWrite() {
  const zeroArgReads = readFunctions.value.filter(fn => !(fn.inputs || []).length)
  const updates = []

  for (const readFn of zeroArgReads) {
    const key = functionKey(readFn)
    const previousValue = readResults.value[key]?.returnValue
    const payload = {
      sessionId: runtimeSessionId.value,
      functionName: readFn.name,
      args: [],
      value: '0'
    }

    try {
      const result = await requestRuntimeCall(payload)
      saveReadResult(readFn, result)
      saveCallHistory(readFn, result)
      updates.push({
        key,
        name: readFn.name,
        previousValue,
        currentValue: result.returnValue
      })
      appendOutputMessage({ text: `[call] ${readFn.name} returned ${formatRuntimeValue(result.returnValue)}.`, muted: true })
    } catch (err) {
      appendOutputMessage({ text: `[warn] ${readFn.name} refresh failed: ${err.message || 'read failed'}`, muted: true })
    }
  }

  return updates
}

async function callRuntimeFunction(fn) {
  if (!runtimeSessionId.value) {
    setFunctionFailure(fn, '请先部署合约')
    return
  }

  const key = functionKey(fn)
  let args = []

  try {
    await nextTick()
    args = buildFunctionArgs(fn)
  } catch (err) {
    setFunctionFailure(fn, err.message || '参数格式不正确')
    return
  }

  isCallingFunction.value = true
  callingFunctionKey.value = key
  functionCallStatus.value = 'calling'
  callError.value = ''
  const payload = {
    sessionId: runtimeSessionId.value,
    functionName: fn.name,
    args,
    value: fn.stateMutability === 'payable' ? getFunctionValue(fn) : '0'
  }
  const callLog = {
    type: 'info',
    source: 'call',
    message: `调用 ${formatFunctionSignature(fn)}${args.length ? `，参数 ${JSON.stringify(args)}` : ''}`
  }
  console.log('[ContractLab] runtime call payload', payload)
  appendOutputMessage(callLog)

  callAbortController?.abort()
  callAbortController = new AbortController()
  const timeoutId = window.setTimeout(() => {
    callAbortController?.abort()
  }, 20000)

  try {
    const result = await requestRuntimeCall(payload, callAbortController.signal)

    functionCallStatus.value = 'success'
    let stateUpdates = []

    if (isReadFunction(fn)) {
      saveReadResult(fn, result)
      appendOutputMessage({
        type: 'success',
        source: 'call',
        message: `${fn.name} 返回 ${formatRuntimeValue(result.returnValue)}`
      })
    } else {
      appendOutputMessage({
        type: 'success',
        source: 'call',
        message: `${fn.name} 调用成功，tx ${shortenHash(result.transactionHash || '')}，Gas Used ${result.gasUsed || '-'}`
      })
      if (Array.isArray(result.events)) {
        result.events.forEach(event => {
          const argsText = Object.entries(normalizeEventArgs(event.args))
            .map(([eventKey, eventValue]) => `${eventKey}=${formatRuntimeValue(eventValue)}`)
            .join(', ')
          appendOutputMessage({
            type: 'info',
            source: 'event',
            message: `${event.eventName || 'Event'}${argsText ? `(${argsText})` : ''}`
          })
        })
      }
      stateUpdates = await refreshReadFunctionsAfterWrite()
    }

    const enrichedResult = {
      ...result,
      stateUpdates
    }
    lastCallResult.value = enrichedResult
    saveCallHistory(fn, enrichedResult)

    saveEventLogs(result)
  } catch (err) {
    let message = '运行服务未启动，请先启动 contract-runtime-service'
    if (err.name === 'AbortError') {
      message = '函数调用请求超时，请检查 contract-runtime-service'
    } else if (err.message === '运行服务返回异常') {
      message = '运行服务返回异常'
    } else if (err.message) {
      message = err.message
    }
    setFunctionFailure(fn, message, err.errorType)
  } finally {
    window.clearTimeout(timeoutId)
    isCallingFunction.value = false
    callingFunctionKey.value = ''
    callAbortController = null
  }
}

async function bootEditor() {
  if (editorInstance.value) {
    editorReady.value = true
    scheduleEditorLayout()
    return
  }

  editorBootId += 1
  editorReady.value = false
  editorLoading.value = true
  editorMessage.value = '正在增强为 Monaco Editor'
  window.clearTimeout(editorLoadTimer)
  editorLoadTimer = window.setTimeout(() => {
    if (editorInstance.value) return
    editorLoading.value = false
    editorMessage.value = '当前使用轻量编辑模式'
  }, 1500)

  try {
    await createEditor()
    window.clearTimeout(editorLoadTimer)
    editorMessage.value = ''
    scheduleEditorLayout()
  } catch (err) {
    window.clearTimeout(editorLoadTimer)
    editorLoading.value = false
    editorMessage.value = '当前使用轻量编辑模式'
  }
}

watch(selectedContractName, (current, previous) => {
  if (current === previous) return
  resetConstructorArgs()
  if (deployStatus.value === 'success' || deployStatus.value === 'failed') {
    resetDeployResult()
  }
})

watch(interactionFunctions, (functions) => {
  if (!functions.length) {
    selectedFunctionKey.value = ''
    return
  }

  if (!functions.some(item => functionKey(item.fn) === selectedFunctionKey.value)) {
    selectedFunctionKey.value = functionKey(functions[0].fn)
  }
}, { immediate: true })

onMounted(() => {
  restoreLayout()
  restoreWorkspace()
  consumeContractDetailSource()
  consumeDraftSource()
  consumePrecheckReport()
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('pointerdown', handleWorkspacePointerDown)
  document.addEventListener('keydown', handleWorkspaceKeydown)
  window.addEventListener('resize', scheduleEditorLayout)
  editorStartTimer = window.setTimeout(bootEditor, 800)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('pointerdown', handleWorkspacePointerDown)
  document.removeEventListener('keydown', handleWorkspaceKeydown)
  window.removeEventListener('resize', scheduleEditorLayout)
  window.removeEventListener('mousemove', handleResizeMove)
  window.removeEventListener('mouseup', stopResize)
  document.body.classList.remove('contract-lab-resizing')
  window.clearTimeout(editorStartTimer)
  window.clearTimeout(editorLoadTimer)
  window.clearTimeout(layoutTimer)
  window.clearTimeout(pendingMessageTimer)
  window.clearTimeout(copiedTimer)
  window.cancelAnimationFrame(layoutFrame)
  compileAbortController?.abort()
  deployAbortController?.abort()
  callAbortController?.abort()
  leanAbortController?.abort()
  clearCompileMarkers()
  contentChangeDisposable?.dispose()
  editorInstance.value?.dispose()
  contentChangeDisposable = null
  editorInstance.value = null
  editorReady.value = false
})
</script>

<style scoped>
.contract-lab-page {
  width: 100%;
  max-width: 100%;
  height: calc(100vh - 64px);
  min-width: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #111827;
  background: #f6f7f8;
}

.contract-lab-page--fullscreen,
.contract-lab-page:fullscreen {
  width: 100vw;
  height: 100vh;
  background: #f6f7f8;
}

.lab-toolbar {
  flex: 0 0 60px;
  min-width: 0;
  padding: 0 14px;
  border: 1px solid #dfe3e8;
  border-bottom: none;
  border-radius: 14px 14px 0 0;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.contract-lab-page--fullscreen .lab-toolbar {
  border-radius: 0;
  border-left: none;
  border-right: none;
  border-top: none;
}

.toolbar-identity,
.toolbar-actions,
.editor-tab {
  min-width: 0;
  display: inline-flex;
  align-items: center;
}

.toolbar-identity {
  gap: 12px;
  overflow: hidden;
}

.toolbar-identity strong {
  color: #111827;
  font-size: 14px;
  font-weight: 850;
  white-space: nowrap;
}

.lab-mode-switch {
  flex: 0 0 auto;
  height: 30px;
  padding: 3px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #f5f6f7;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.lab-mode-switch button {
  height: 22px;
  padding: 0 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #667085;
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.lab-mode-switch button.active {
  background: #111827;
  color: #ffffff;
}

.lab-mode-switch button:not(.active):hover {
  background: #ffffff;
  color: #111827;
}

.toolbar-identity span {
  min-width: 0;
  padding-left: 12px;
  border-left: 1px solid #e5e7eb;
  color: #667085;
  font-size: 12px;
  font-weight: 750;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.example-select {
  flex: 0 0 auto;
  min-width: 0;
  height: 32px;
  padding-left: 12px;
  border-left: 1px solid #e5e7eb;
  display: inline-flex;
  align-items: center;
}

.example-select select {
  min-width: 0;
  width: 132px;
  height: 32px;
  padding: 0 32px 0 11px;
  border: 1px solid #cfd5dd;
  border-radius: 9px;
  background:
    linear-gradient(180deg, #ffffff 0%, #fbfbfc 100%);
  color: #111827;
  font-size: 12px;
  font-weight: 850;
  outline: none;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(17, 24, 39, 0.04);
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    background 0.16s ease;
}

.example-select select:hover {
  border-color: #b8c0cc;
  background: #ffffff;
}

.example-select select:focus {
  border-color: #111827;
  box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.08);
}

.toolbar-actions {
  flex: 0 0 auto;
  gap: 8px;
}

.tool-button {
  height: 32px;
  padding: 0 11px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  color: #475467;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease;
}

.tool-button:hover:not(:disabled) {
  background: #f5f6f7;
  border-color: #cfd5dd;
  color: #111827;
}

.tool-button:disabled {
  cursor: default;
  opacity: 0.64;
}

.tool-button--primary {
  background: #111827;
  border-color: #111827;
  color: #ffffff;
}

.tool-button--primary:hover:not(:disabled) {
  background: #1f2937;
  border-color: #1f2937;
  color: #ffffff;
}

.tool-button--active {
  background: #111827;
  border-color: #111827;
  color: #ffffff;
}

.tool-button--active:hover:not(:disabled) {
  background: #1f2937;
  border-color: #1f2937;
  color: #ffffff;
}

.tool-button--icon svg {
  width: 15px;
  height: 15px;
}

.fullscreen-message {
  flex: 0 0 auto;
  margin: 0;
  padding: 8px 12px;
  border: 1px solid #dfe3e8;
  border-bottom: none;
  background: #ffffff;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.lab-confirm {
  flex: 0 0 auto;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #dfe3e8;
  border-bottom: none;
  background: #ffffff;
  display: flex;
  align-items: center;
  gap: 12px;
}

.lab-confirm strong {
  color: #111827;
  font-size: 13px;
  font-weight: 850;
}

.lab-confirm p {
  flex: 1;
  margin: 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

.lab-confirm div {
  display: inline-flex;
  gap: 8px;
}

.lab-confirm button,
.workspace-confirm button {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #dfe3e8;
  border-radius: 7px;
  background: #ffffff;
  color: #475467;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.lab-confirm button:last-child,
.workspace-confirm button:last-child {
  background: #111827;
  border-color: #111827;
  color: #ffffff;
}

.lab-workbench {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns: var(--workspace-width) 6px minmax(0, 1fr) 6px var(--result-width);
  grid-template-rows: minmax(0, 1fr) var(--dock-height);
  border: 1px solid #dfe3e8;
  border-radius: 0 0 14px 14px;
  background: #ffffff;
  overflow: hidden;
}

.contract-lab-page--fullscreen .lab-workbench {
  border-left: none;
  border-right: none;
  border-bottom: none;
  border-radius: 0;
}

.lab-panel,
.lab-editor {
  min-width: 0;
  min-height: 0;
  background: #ffffff;
}

.lab-panel {
  overflow: hidden;
}

.lab-panel--files {
  grid-column: 1;
  grid-row: 1;
  padding: 12px 10px 18px;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: #cfd5dd transparent;
}

.lab-panel--files::-webkit-scrollbar {
  width: 7px;
}

.lab-panel--files::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cfd5dd;
}

.lab-panel--files::-webkit-scrollbar-track {
  background: transparent;
}

.splitter {
  min-width: 0;
  min-height: 0;
  background: transparent;
  position: relative;
  z-index: 5;
}

.splitter::after {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  transition: background 0.16s ease;
}

.splitter:hover::after {
  background: #dfe3e8;
}

.splitter--left {
  grid-column: 2;
  grid-row: 1;
  cursor: col-resize;
}

.splitter--right {
  grid-column: 4;
  grid-row: 1;
  cursor: col-resize;
}

.splitter--dock {
  position: absolute;
  top: -4px;
  left: 0;
  right: 0;
  height: 8px;
  cursor: row-resize;
}

:global(.contract-lab-resizing) {
  user-select: none;
  cursor: col-resize;
}

.panel-head {
  height: 34px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.panel-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.panel-actions button {
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: #475467;
  font-size: 17px;
  line-height: 1;
  font-weight: 850;
  cursor: pointer;
}

.workspace-menu {
  position: fixed;
  z-index: 100;
  width: 174px;
  padding: 5px;
  border: 1px solid #dfe3e8;
  border-radius: 9px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(17, 24, 39, 0.12);
  display: grid;
  gap: 2px;
}

.workspace-menu button {
  min-width: 0;
  height: 30px;
  padding: 0 9px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: #374151;
  text-align: left;
  font-size: 12px;
  font-weight: 760;
  cursor: pointer;
}

.workspace-menu button:hover {
  background: #f4f5f6;
  color: #111827;
}

.panel-actions button:hover,
.tree-more:hover {
  background: #eef0f3;
  color: #111827;
}

.tree-section {
  display: grid;
  gap: 2px;
  padding: 8px 0 10px;
}

.tree-section + .tree-section {
  border-top: 1px solid #eef1f4;
}

.tree-section p {
  margin: 0 0 5px;
  padding: 0 8px;
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.06em;
}

.tree-folder,
.file-item,
.template-item {
  width: 100%;
  min-width: 0;
  min-height: 32px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #667085;
  display: flex;
  align-items: center;
  gap: 7px;
  text-align: left;
  font-size: 12px;
  font-weight: 720;
}

.tree-inline-row {
  width: 100%;
  min-width: 0;
  height: 30px;
  padding: 0 8px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  gap: 7px;
}

.tree-inline-row svg {
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  color: #667085;
}

.tree-inline-row--folder {
  padding-left: 20px;
}

.tree-inline-input {
  min-width: 0;
  width: 100%;
  height: 23px;
  padding: 0 6px;
  border: 1px solid #9ca3af;
  border-radius: 4px;
  background: #ffffff;
  color: #111827;
  font-size: 12px;
  font-weight: 760;
  outline: none;
}

.tree-folder-block {
  display: grid;
  gap: 2px;
}

.tree-folder {
  padding: 0 8px 0 5px;
}

.tree-folder span:nth-child(2) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item,
.template-item {
  padding: 0 8px;
  cursor: pointer;
  transition:
    background 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease;
}

.tree-more {
  margin-left: auto;
  width: 22px;
  height: 22px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 850;
  cursor: pointer;
  opacity: 0;
}

.tree-folder:hover .tree-more,
.file-item:hover .tree-more,
.file-item.active .tree-more {
  opacity: 1;
}

.tree-caret {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: #667085;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 900;
  line-height: 1;
  transition:
    background 0.16s ease,
    color 0.16s ease;
}

.tree-folder:hover .tree-caret {
  background: #eef0f3;
  color: #111827;
}

.file-indent {
  flex: 0 0 18px;
}

.file-item svg {
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  color: #667085;
}

.file-item span:last-child,
.template-item span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item > span:not(.file-indent) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item.active,
.template-item.active {
  background: #eef0f3;
  color: #111827;
  font-weight: 850;
}

.file-item:hover,
.template-item:hover {
  background: #f4f5f6;
  color: #111827;
}

.workspace-confirm-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  background: rgba(255, 255, 255, 0.58);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.workspace-confirm {
  width: min(360px, 100%);
  padding: 16px;
  border: 1px solid #dfe3e8;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 22px 60px rgba(17, 24, 39, 0.16);
  display: grid;
  gap: 10px;
}

.workspace-confirm strong {
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.workspace-confirm p {
  margin: 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

.workspace-confirm div {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.lab-editor {
  grid-column: 3;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-toolbar {
  flex: 0 0 42px;
  padding: 0 12px;
  border-bottom: 1px solid #e5e7eb;
  background: #fbfbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.editor-tab {
  height: 100%;
  gap: 9px;
  color: #111827;
}

.editor-tab strong {
  font-size: 12px;
  font-weight: 850;
}

.editor-tab span:last-child {
  color: #98a2b3;
  font-size: 11px;
  font-weight: 800;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #111827;
}

.editor-message {
  flex: 0 0 auto;
  margin: 0;
  padding: 7px 12px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.editor-save-button {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  color: #667085;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition:
    background 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease;
}

.editor-save-button:hover {
  background: #f5f6f7;
  border-color: #cfd5dd;
  color: #111827;
}

.editor-save-button svg {
  width: 15px;
  height: 15px;
}

.editor-frame {
  position: relative;
  min-height: 0;
  flex: 1;
  overflow: hidden;
  background: #ffffff;
}

.monaco-host {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #ffffff;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}

.monaco-host--ready {
  opacity: 1;
  pointer-events: auto;
}

.fallback-editor {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 14px 18px;
  border: none;
  outline: none;
  resize: none;
  background: #ffffff;
  color: #111827;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 13px;
  line-height: 22px;
  tab-size: 4;
  white-space: pre;
  overflow: auto;
}

.lab-panel--display {
  grid-column: 5;
  grid-row: 1;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.proof-lab-editor {
  background: #ffffff;
}

.proof-lab-editor .proof-editor-panel {
  height: 100%;
}

.universal-panel {
  background: #ffffff;
}

.universal-panel-tabs {
  flex: 0 0 38px;
  min-width: 0;
  border-bottom: 1px solid #e5e7eb;
  background: #fbfbfc;
  display: flex;
  align-items: stretch;
  position: relative;
}

.panel-tab-strip {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: stretch;
  overflow-x: auto;
  overflow-y: hidden;
}

.panel-tab {
  flex: 0 0 auto;
  max-width: 132px;
  min-width: 74px;
  padding: 0 5px 0 10px;
  border-right: 1px solid #eef1f4;
  background: transparent;
  color: #667085;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  cursor: pointer;
}

.panel-tab.active {
  background: #ffffff;
  color: #111827;
  box-shadow: inset 0 -2px 0 #111827;
}

.panel-tab span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  font-weight: 850;
}

.panel-tab button {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: #98a2b3;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.45;
}

.panel-tab:hover button,
.panel-tab.active button {
  opacity: 1;
}

.panel-tab button:hover {
  background: #eef0f3;
  color: #111827;
}

.panel-tab-menu-wrap {
  flex: 0 0 auto;
  position: relative;
  border-left: 1px solid #eef1f4;
}

.panel-tab-add {
  width: 36px;
  height: 100%;
  border: none;
  background: transparent;
  color: #667085;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
}

.panel-tab-add:hover {
  background: #f5f6f7;
  color: #111827;
}

.panel-add-menu {
  position: absolute;
  z-index: 50;
  top: 36px;
  right: 6px;
  width: 132px;
  padding: 5px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(17, 24, 39, 0.14);
  display: grid;
  gap: 2px;
}

.panel-add-menu button {
  height: 28px;
  padding: 0 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #475467;
  text-align: left;
  font-size: 12px;
  font-weight: 760;
  cursor: pointer;
}

.panel-add-menu button:hover,
.panel-add-menu button.active {
  background: #f4f5f6;
  color: #111827;
}

.panel-tab-content {
  min-height: 0;
  flex: 1;
  display: flex;
  overflow: hidden;
  background: #ffffff;
}

.panel-section-head,
.panel-chat-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.panel-section-head strong {
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.panel-chat-toolbar button {
  height: 26px;
  padding: 0 8px;
  border: 1px solid #dfe3e8;
  border-radius: 6px;
  background: #ffffff;
  color: #667085;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
}

.panel-chat-toolbar button:hover {
  background: #f5f6f7;
  color: #111827;
}

.chat-panel {
  min-height: 0;
  flex: 1;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.chat-model-row {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-model-row span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.chat-model-row select {
  min-width: 0;
  flex: 1;
  height: 28px;
  padding: 0 8px;
  border: 1px solid #dfe3e8;
  border-radius: 6px;
  background: #ffffff;
  color: #111827;
  font-size: 12px;
  font-weight: 760;
  outline: none;
}

.chat-attachment {
  flex: 0 0 auto;
  padding: 8px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #f8fafc;
  display: grid;
  gap: 7px;
}

.chat-attachment-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.chat-attachment-main strong {
  display: block;
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.chat-attachment-main p {
  margin: 2px 0 0;
  color: #667085;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.45;
}

.attachment-close-btn {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #98a2b3;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.attachment-close-btn:hover {
  background: #eef0f3;
  color: #111827;
}

.chat-attachment-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chat-attachment-actions button {
  height: 26px;
  padding: 0 8px;
  border: 1px solid #dfe3e8;
  border-radius: 6px;
  background: #ffffff;
  color: #344054;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
}

.chat-attachment-actions button:first-child {
  border-color: #111827;
  background: #111827;
  color: #ffffff;
}

.chat-attachment-actions button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.chat-attachment-issues {
  display: grid;
  gap: 6px;
  padding-top: 2px;
}

.chat-attachment-issue {
  display: grid;
  gap: 2px;
  padding-top: 6px;
  border-top: 1px solid #e5e7eb;
}

.chat-attachment-issue span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.04em;
}

.chat-attachment-issue strong {
  color: #111827;
  font-size: 11px;
  font-weight: 850;
}

.chat-attachment-issue p {
  margin: 0;
  color: #667085;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  padding: 4px 2px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
}

.chat-empty {
  margin: auto;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 760;
}

.chat-message {
  max-width: 92%;
  padding: 8px 9px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  display: grid;
  gap: 4px;
}

.chat-message--user {
  align-self: flex-end;
  background: #f5f6f7;
}

.chat-message--assistant,
.chat-message--system {
  align-self: flex-start;
}

.chat-message--assistant {
  border-color: #e6eaf0;
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfd 100%);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.045);
}

.chat-message span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.chat-message pre {
  margin: 0;
  color: #374151;
  font-family: inherit;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-message :deep(.markdown-message) {
  font-size: 12px;
  line-height: 1.62;
}

.chat-message :deep(.markdown-message h1) {
  font-size: 15px;
  margin-bottom: 8px;
}

.chat-message :deep(.markdown-message h2) {
  font-size: 14px;
  margin: 10px 0 6px;
}

.chat-message :deep(.markdown-message h3) {
  font-size: 13px;
  margin: 8px 0 5px;
}

.chat-message :deep(.markdown-message pre) {
  padding: 9px;
  border-radius: 8px;
}

.chat-model-used {
  margin: 6px 0 0;
  color: #98a2b3;
  font-size: 10px;
  font-weight: 700;
}

.chat-loading-bubble {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.chat-loading-bubble span {
  color: #475467;
  font-size: 12px;
  font-weight: 700;
  text-transform: none;
  letter-spacing: 0;
}

.chat-loading-bubble i {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: #98a2b3;
  animation: chatLoadingDot 1.2s ease-in-out infinite;
}

.chat-loading-bubble i:nth-child(3) {
  animation-delay: 0.15s;
}

.chat-loading-bubble i:nth-child(4) {
  animation-delay: 0.3s;
}

.chat-message-actions {
  align-self: flex-start;
  display: flex;
  align-items: center;
  gap: 3px;
  margin-top: -4px;
  margin-left: 6px;
}

.chat-message-actions button {
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #7a8493;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.chat-message-actions button:hover:not(:disabled) {
  background: #f1f3f6;
  color: #111827;
  transform: translateY(-0.5px);
}

.chat-message-actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.chat-message-actions svg {
  width: 14px;
  height: 14px;
}

@keyframes chatLoadingDot {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.chat-message--proposal {
  width: 100%;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
}

.proposal-card {
  width: 100%;
  padding: 10px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  display: grid;
  gap: 8px;
}

.proposal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.proposal-head strong {
  display: block;
  color: #111827;
  font-size: 12px;
  font-weight: 860;
  line-height: 1.35;
}

.proposal-head p {
  margin: 3px 0 0;
  color: #667085;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.45;
}

.proposal-head > span {
  flex: 0 0 auto;
  padding: 2px 6px;
  border: 1px solid #dfe3e8;
  border-radius: 999px;
  color: #667085;
  background: #f8fafc;
  font-size: 10px;
  font-weight: 850;
}

.proposal-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.proposal-meta span {
  padding: 2px 6px;
  border-radius: 999px;
  background: #f5f6f7;
  color: #667085;
  font-size: 10px;
  font-weight: 820;
  text-transform: none;
  letter-spacing: 0;
}

.proposal-changes {
  margin: 0;
  padding-left: 16px;
  color: #475467;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.5;
}

.proposal-diff {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
  background: #fbfcfe;
  overflow: hidden;
}

.proposal-diff-head {
  height: 30px;
  padding: 0 8px;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.proposal-diff-head span {
  color: #667085;
  font-size: 10px;
  font-weight: 850;
}

.proposal-diff-head button,
.proposal-actions button {
  height: 24px;
  padding: 0 7px;
  border: 1px solid #dfe3e8;
  border-radius: 6px;
  background: #ffffff;
  color: #374151;
  font-size: 11px;
  font-weight: 780;
  cursor: pointer;
}

.proposal-actions button:disabled {
  opacity: 0.45;
  cursor: default;
}

.proposal-diff pre {
  max-height: 220px;
  margin: 0;
  padding: 8px;
  overflow: auto;
  color: #374151;
  background: transparent;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre;
}

.diff-line {
  display: block;
}

.diff-line--add {
  color: #166534;
  background: #f0fdf4;
}

.diff-line--remove {
  color: #991b1b;
  background: #fef2f2;
}

.diff-line--context {
  color: #667085;
}

.proposal-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chat-composer {
  flex: 0 0 auto;
  display: grid;
  gap: 7px;
}

.chat-composer textarea {
  width: 100%;
  min-height: 70px;
  max-height: 140px;
  padding: 9px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  resize: vertical;
  outline: none;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.5;
}

.chat-composer textarea:focus {
  border-color: #9ca3af;
}

.chat-composer button {
  height: 30px;
  border: 1px solid #111827;
  border-radius: 7px;
  background: #111827;
  color: #ffffff;
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.chat-composer button:disabled {
  cursor: default;
  opacity: 0.6;
}

.display-action {
  height: 28px;
  padding: 0 9px;
  border: 1px solid #dfe3e8;
  border-radius: 7px;
  background: #ffffff;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.display-action:hover {
  background: #f5f6f7;
  color: #111827;
}

.bottom-dock {
  grid-column: 1 / -1;
  grid-row: 2;
  min-width: 0;
  min-height: 0;
  height: auto;
  border-top: 1px solid #e5e7eb;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.bottom-dock--collapsed {
  height: auto;
}

.dock-head {
  flex: 0 0 34px;
  border-bottom: 1px solid #eef1f4;
  background: #fbfbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dock-title {
  min-width: 0;
  padding-left: 12px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dock-title strong {
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.dock-title span {
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: #eef0f3;
  color: #475467;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 850;
}

.dock-actions {
  height: 100%;
  display: inline-flex;
  align-items: stretch;
}

.dock-actions button {
  width: 40px;
  padding: 0;
  border: none;
  background: transparent;
  color: #667085;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-left: 1px solid #eef1f4;
}

.dock-actions button svg {
  width: 15px;
  height: 15px;
}

.dock-actions button:hover:not(:disabled) {
  background: #f5f6f7;
  color: #111827;
}

.dock-actions button:disabled {
  cursor: default;
  opacity: 0.52;
}

.dock-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.dock-pane {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 10px;
  overflow: auto;
}

.dock-empty {
  height: 100%;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #667085;
  text-align: center;
}

.dock-empty strong {
  color: #111827;
  font-size: 13px;
  font-weight: 850;
}

.dock-empty p {
  margin: 7px 0 0;
  font-size: 12px;
}

.result-view {
  width: 100%;
  min-width: 0;
  min-height: 0;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
}

.result-section {
  min-width: 0;
  display: grid;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eef1f4;
}

.result-section:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.result-section-head {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.result-section-head strong {
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.result-section-head span {
  color: #98a2b3;
  font-size: 11px;
  font-weight: 780;
}

.result-section-head div {
  display: inline-flex;
  gap: 5px;
}

.result-section-head button {
  height: 24px;
  padding: 0 7px;
  border: 1px solid #dfe3e8;
  border-radius: 5px;
  background: #ffffff;
  color: #475467;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
}

.result-section-head button:hover:not(:disabled) {
  background: #f5f6f7;
  color: #111827;
}

.result-section-head button:disabled {
  cursor: default;
  opacity: 0.52;
}

.result-empty-line {
  margin: 0;
  color: #667085;
  font-size: 12px;
}

.compiler-report {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fbfbfc;
  overflow: hidden;
}

.compiler-report--failed {
  background: #f7f7f8;
  border-color: #d7dce3;
}

.compiler-compat-note {
  padding: 10px;
  border-top: 1px solid #e5e7eb;
  background: #ffffff;
}

.compiler-compat-note strong {
  display: block;
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.compiler-compat-note p {
  margin: 5px 0 0;
  color: #667085;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.55;
}

.report-line {
  min-height: 30px;
  padding: 6px 10px;
  border-bottom: 1px solid #eef1f4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.report-line:last-child {
  border-bottom: none;
}

.report-line span {
  flex: 0 0 82px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 850;
}

.report-line strong {
  min-width: 0;
  color: #374151;
  font-size: 12px;
  font-weight: 820;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  overflow-wrap: anywhere;
}

.report-line--strong strong {
  color: #111827;
}

.report-line--copyable button {
  min-width: 0;
  border: none;
  background: transparent;
  color: #374151;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-line--copyable button:hover {
  color: #111827;
}

.report-line--copyable small {
  flex: 0 0 auto;
  color: #98a2b3;
  font-family: inherit;
  font-size: 10px;
  font-weight: 850;
}

.constructor-panel {
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
  display: grid;
  gap: 9px;
}

.constructor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.constructor-head strong {
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.constructor-head span,
.constructor-field span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
}

.constructor-field {
  display: grid;
  gap: 5px;
}

.constructor-field input {
  width: 100%;
  height: 31px;
  padding: 0 9px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #fbfbfc;
  color: #111827;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 12px;
  outline: none;
}

.constructor-field input:focus {
  border-color: #9ca3af;
  background: #ffffff;
}

.interaction-browser {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
}

.panel-interaction {
  width: 100%;
  min-width: 0;
  min-height: 0;
  padding: 10px;
  display: flex;
  overflow: hidden;
}

.interaction-browser--panel {
  width: 100%;
  grid-template-columns: 1fr;
  grid-template-rows: minmax(120px, 190px) minmax(0, 1fr);
}

.interaction-browser--panel .function-nav {
  border-right: none;
  border-bottom: 1px solid #e5e7eb;
}

.interaction-browser--panel .function-nav-group button strong,
.interaction-browser--panel .function-nav-group button span,
.interaction-browser--panel .function-detail-head strong {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  overflow-wrap: anywhere;
}

.interaction-browser--panel .inspector-table {
  border: none;
  border-radius: 0;
  display: grid;
  gap: 8px;
}

.interaction-browser--panel .inspector-row--head {
  display: none;
}

.interaction-browser--panel .inspector-row {
  min-height: 0;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 5px;
  align-items: stretch;
}

.interaction-browser--panel .inspector-row span {
  padding: 0;
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  overflow-wrap: anywhere;
}

.interaction-browser--panel .inspector-row span:nth-child(2) {
  color: #98a2b3;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 10px;
}

.interaction-browser--panel .inspector-row span:nth-child(2)::before {
  content: 'type: ';
}

.interaction-browser--panel .inspector-value-cell {
  padding: 0;
}

.interaction-browser--panel .inspector-row input,
.interaction-browser--panel .inspector-value-cell input {
  width: 100%;
  height: 32px;
  margin: 0;
}

.interaction-browser--panel .function-action-row {
  justify-content: stretch;
}

.interaction-browser--panel .function-action-row button {
  width: 100%;
}

.function-nav {
  min-width: 0;
  min-height: 0;
  padding: 8px;
  border-right: 1px solid #e5e7eb;
  background: #fbfbfc;
  overflow: auto;
}

.function-nav-group {
  display: grid;
  gap: 4px;
}

.function-nav-group + .function-nav-group {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #eef1f4;
}

.function-nav-group h3,
.parameter-panel h3,
.detail-result-panel h3 {
  margin: 2px 0 0;
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.function-nav-group button {
  min-width: 0;
  min-height: 40px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #667085;
  display: grid;
  gap: 2px;
  text-align: left;
  cursor: pointer;
}

.function-nav-group button:hover,
.function-nav-group button.active {
  background: #eef0f3;
  color: #111827;
}

.function-nav-group button strong {
  min-width: 0;
  font-size: 12px;
  font-weight: 850;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.function-nav-group button span {
  min-width: 0;
  color: #98a2b3;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.function-detail {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.function-detail-head {
  flex: 0 0 auto;
  min-height: 50px;
  padding: 9px 12px;
  border-bottom: 1px solid #eef1f4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.function-detail-head div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.function-detail-head span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.function-detail-head strong {
  min-width: 0;
  color: #111827;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 12px;
  font-weight: 850;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.function-detail-body {
  min-height: 0;
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
}

.inspector-section {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.inspector-section h3 {
  margin: 0;
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.inspector-table {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
  background: #ffffff;
}

.inspector-row {
  min-height: 34px;
  display: grid;
  grid-template-columns: minmax(90px, 0.8fr) minmax(90px, 0.7fr) minmax(0, 1.6fr);
  align-items: center;
  border-bottom: 1px solid #eef1f4;
}

.inspector-row:last-child {
  border-bottom: none;
}

.inspector-row--head {
  background: #f5f6f7;
}

.inspector-row span {
  min-width: 0;
  padding: 0 9px;
  color: #475467;
  font-size: 11px;
  font-weight: 760;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inspector-row--head span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.inspector-row input {
  width: calc(100% - 8px);
  height: 26px;
  margin-right: 8px;
  padding: 0 8px;
  border: 1px solid #dfe3e8;
  border-radius: 5px;
  background: #fbfbfc;
  color: #111827;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 11px;
  outline: none;
}

.inspector-value-cell {
  min-width: 0;
  padding: 4px 0;
}

.inspector-value-cell input {
  width: calc(100% - 8px);
}

.inspector-value-cell input::placeholder {
  color: #a8b0bd;
}

.inspector-value-cell small {
  display: block;
  margin: 3px 8px 0 0;
  color: #7f1d1d;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.25;
}

.inspector-row input:focus {
  border-color: #9ca3af;
  background: #ffffff;
}

.muted-value {
  color: #98a2b3;
}

.result-value {
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  white-space: normal !important;
  overflow-wrap: anywhere;
}

.function-action-row {
  display: flex;
  justify-content: flex-end;
}

.function-action-row button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid #111827;
  border-radius: 6px;
  background: #111827;
  color: #ffffff;
  font-size: 11px;
  font-weight: 850;
  cursor: pointer;
}

.function-action-row button:hover:not(:disabled) {
  background: #1f2937;
  border-color: #1f2937;
}

.function-action-row button:disabled {
  cursor: default;
  opacity: 0.62;
}

.compile-loading {
  min-height: 64px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fbfbfc;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #374151;
  font-size: 12px;
  font-weight: 850;
}

.compile-loading span {
  width: 16px;
  height: 16px;
  border: 2px solid #e5e7eb;
  border-top-color: #111827;
  border-radius: 999px;
  animation: spin 0.8s linear infinite;
}

.diagnostic-list {
  display: grid;
  gap: 7px;
}

.diagnostic-list h3 {
  margin: 0;
  color: #111827;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0.04em;
}

.diagnostic {
  min-width: 0;
  padding: 9px;
  border: 1px solid #e5e7eb;
  border-radius: 9px;
  background: #ffffff;
  display: grid;
  gap: 5px;
}

.diagnostic--error {
  border-color: #cfd5dd;
  background: #f7f7f8;
}

.diagnostic--warning {
  border-color: #dfe3e8;
  background: #fbfbfc;
}

.diagnostic span,
.contract-picker label {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
}

.diagnostic strong {
  color: #374151;
  font-size: 12px;
  line-height: 1.5;
  font-weight: 760;
}

.diagnostic pre,
.abi-panel pre {
  max-height: 260px;
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fbfbfc;
  color: #111827;
  overflow: auto;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 11px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.console-lines {
  height: 100%;
  padding: 6px 10px;
  border-radius: 0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 0;
  color: #111827;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 11px;
  overflow: auto;
}

.proof-result-summary {
  min-height: 32px;
  padding: 7px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fbfbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.proof-result-summary strong {
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.proof-result-summary span {
  color: #667085;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 11px;
  font-weight: 800;
}

.proof-result-summary--passed {
  background: #ffffff;
  border-color: #cfd5dd;
}

.proof-result-summary--failed {
  background: #f7f7f8;
  border-color: #cfd5dd;
}

.proof-placeholder--dock,
.proof-pass--dock {
  min-height: 0;
}

.proof-placeholder--dock p,
.proof-pass--dock p {
  margin: 0;
}

.proof-diagnostics--dock {
  display: grid;
  gap: 7px;
}

.proof-editor-panel {
  min-width: 0;
  min-height: 0;
}

.proof-editor-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.proof-editor-panel--mode {
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
}

.lean-editor {
  flex: 1;
  min-height: 0;
  width: 100%;
  padding: 14px 16px;
  border: none;
  outline: none;
  resize: none;
  background: #ffffff;
  color: #111827;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 12px;
  line-height: 1.7;
  tab-size: 2;
  white-space: pre;
  overflow: auto;
}

.lean-editor--mode {
  min-height: 420px;
}

.proof-pass strong,
.proof-diagnostics > strong,
.proof-placeholder strong {
  color: #111827;
  font-size: 12px;
  font-weight: 850;
}

.proof-service-message,
.proof-pass,
.proof-placeholder {
  padding: 10px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
}

.proof-service-message {
  color: #374151;
  font-size: 12px;
  font-weight: 760;
  line-height: 1.5;
}

.proof-pass {
  border-color: #cfd5dd;
}

.proof-pass p,
.proof-placeholder p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.55;
}

.proof-diagnostics {
  display: grid;
  gap: 7px;
}

.proof-diagnostics--warnings {
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
}

.console-line {
  margin: 0;
  padding: 2px 0;
  border-left: none;
  border-radius: 0;
  background: transparent;
  display: grid;
  gap: 2px;
}

.console-line--success {
  color: #166534;
}

.console-line--warning {
  color: #854d0e;
}

.console-line--error {
  color: #991b1b;
}

.console-line-main {
  min-width: 0;
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
}

.console-line-main span {
  color: #98a2b3;
}

.console-line-main strong {
  color: #475467;
  font-weight: 850;
}

.console-line-main p {
  min-width: 0;
  margin: 0;
  color: #111827;
  overflow-wrap: anywhere;
}

.console-line--success .console-line-main p {
  color: #166534;
}

.console-line--warning .console-line-main p {
  color: #854d0e;
}

.console-line--error .console-line-main p {
  color: #991b1b;
}

.console-line pre {
  margin: 0;
  padding: 2px 0 2px 105px;
  border-radius: 0;
  background: transparent;
  color: #667085;
  white-space: pre-wrap;
  overflow: auto;
}

.console-line.muted .console-line-main p {
  color: #9ca3af;
}

.contract-picker {
  display: grid;
  gap: 6px;
}

.contract-picker select {
  width: 100%;
  height: 32px;
  padding: 0 9px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  font-size: 12px;
  font-weight: 760;
  outline: none;
}

.abi-panel {
  min-width: 0;
  display: grid;
  gap: 9px;
}

.abi-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.abi-meta span {
  min-width: 0;
  color: #111827;
  font-size: 12px;
  font-weight: 850;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.abi-meta strong,
.abi-panel p {
  color: #667085;
  font-size: 11px;
  font-weight: 780;
}

.abi-panel p {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-empty {
  width: 100%;
  min-height: 140px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.stage-empty--compact {
  min-height: 132px;
}

.stage-empty strong {
  color: #111827;
  font-size: 13px;
  font-weight: 850;
}

.stage-empty p {
  max-width: 230px;
  margin: 8px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.event-log-list {
  width: 100%;
  display: grid;
  gap: 9px;
}

.event-log-item {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 9px;
  background: #ffffff;
  text-align: left;
}

.event-log-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 7px;
}

.event-log-title span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 850;
}

.event-log-title strong {
  min-width: 0;
  color: #111827;
  font-size: 12px;
  font-weight: 850;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-log-item p {
  max-width: none;
  margin: 4px 0 0;
  display: grid;
  grid-template-columns: minmax(72px, 0.8fr) minmax(0, 1.8fr);
  gap: 8px;
  color: #374151;
  font-family: 'SFMono-Regular', 'Cascadia Code', 'Menlo', monospace;
  font-size: 11px;
  line-height: 1.5;
  word-break: break-word;
}

.event-log-item p span {
  color: #98a2b3;
  font-family: inherit;
  font-weight: 800;
}

.event-log-item p strong {
  color: #111827;
  font-family: inherit;
  font-size: inherit;
  font-weight: 760;
  word-break: break-word;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1280px) {
  .lab-workbench {
    grid-template-columns: 220px 6px minmax(0, 1fr) 6px minmax(320px, 360px);
  }

}

@media (max-width: 1100px) {
  .contract-lab-page:not(.contract-lab-page--fullscreen) {
    height: auto;
    min-height: calc(100vh - 64px);
    overflow: visible;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .lab-workbench {
    min-height: 760px;
    grid-template-columns: 220px 6px minmax(0, 1fr) 6px minmax(300px, 340px);
    grid-template-rows: minmax(0, 1fr) var(--dock-height);
    overflow: visible;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .lab-panel--files {
    grid-column: 1;
    grid-row: 1 / span 2;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .splitter--left {
    grid-column: 2;
    grid-row: 1;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .lab-editor {
    grid-column: 3;
    grid-row: 1;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .lab-panel--display {
    grid-column: 5;
    grid-row: 1;
    min-height: 0;
    border-left: 1px solid #e5e7eb;
    border-top: none;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .bottom-dock {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .contract-lab-page--fullscreen .lab-workbench {
    grid-template-columns: 210px 6px minmax(0, 1fr) 6px 340px;
  }

  .interaction-pane {
    grid-template-columns: 1fr;
  }

  .interaction-browser {
    grid-template-columns: 200px minmax(0, 1fr);
  }

  .function-detail-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .contract-lab-page {
    width: 100%;
    height: auto;
    max-width: 100%;
  }

  .contract-lab-page--fullscreen {
    height: 100vh;
    overflow: hidden;
  }

  .lab-toolbar {
    flex-wrap: wrap;
    height: auto;
    min-height: 60px;
    padding: 10px;
    align-items: flex-start;
  }

  .toolbar-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .lab-workbench,
  .contract-lab-page--fullscreen .lab-workbench {
    grid-template-rows: auto minmax(420px, 1fr) auto auto;
    overflow: auto;
  }

  .contract-lab-page:not(.contract-lab-page--fullscreen) .lab-workbench,
  .contract-lab-page--fullscreen .lab-workbench {
    grid-template-columns: 1fr !important;
  }

  .lab-panel--files,
  .lab-panel--display {
    border-left: none;
    border-right: none;
  }

  .lab-panel--files {
    grid-column: 1;
    grid-row: 1;
    min-height: 260px;
    border-bottom: 1px solid #e5e7eb;
  }

  .lab-editor {
    grid-column: 1;
    grid-row: 2;
    min-height: 460px;
  }

  .lab-panel--display {
    grid-column: 1;
    grid-row: 3;
    min-height: 360px;
    border-top: 1px solid #e5e7eb;
  }

  .bottom-dock {
    grid-column: 1;
    grid-row: 4;
  }

  .proof-editor-panel--mode {
    min-height: 360px;
    border-right: none;
  }

  .interaction-panel {
    grid-template-columns: 1fr;
  }

  .interaction-browser {
    grid-template-columns: 1fr;
  }

  .function-nav {
    max-height: 170px;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }

}
</style>
