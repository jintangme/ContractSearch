<template>
  <div class="profile-page">
    <div class="page-header">
      <p class="page-eyebrow">ACCOUNT CENTER</p>
      <h1 class="page-title">个人中心</h1>
      <p class="page-subtitle">管理账户信息、收藏夹，以及智能预审后的合约草稿和收录申请。</p>
    </div>

    <section class="account-hero">
      <div class="account-avatar" aria-hidden="true">
        <img v-if="currentAvatarSrc" :src="currentAvatarSrc" alt="" @error="handleAvatarImageError" />
        <span v-else>{{ profileInitial }}</span>
      </div>
      <div class="account-identity">
        <p class="account-kicker">个人工作台</p>
        <h2>{{ displayName }}</h2>
        <p>{{ userStore.profile?.email || '未填写邮箱' }}</p>
      </div>
      <button class="action-btn action-btn--secondary account-edit-btn" @click="openEditProfile">
        编辑资料
      </button>
    </section>

    <div class="tabs" role="tablist" aria-label="个人中心导航">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'favorites' }"
        @click="switchToFavorites"
        role="tab"
      >
        收藏夹
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'contracts' }"
        @click="switchToMyContracts"
        role="tab"
      >
        我的合约
      </button>
    </div>

    <div v-if="pageLoading" class="loading-state">
      <span class="spinner"></span>
      <span>加载中...</span>
    </div>

    <template v-else>
      <template v-if="activeTab === 'favorites'">
        <div v-if="favLoading" class="loading-state">
          <span class="spinner"></span>
          <span>加载收藏夹...</span>
        </div>

        <p v-else-if="favError" class="error-msg">{{ favError }}</p>

        <template v-else>
          <section class="favorite-workspace">
            <div class="favorite-workspace__head">
              <div>
                <h2 class="section-title">收藏夹</h2>
                <p>收藏合约按文件夹归档，进入文件夹查看内容。</p>
              </div>
              <button
                v-if="!selectedFolder && !creatingFolderInline"
                class="action-btn action-btn--primary"
                @click="startCreateFolder"
              >
                新建文件夹
              </button>
              <form v-else-if="!selectedFolder" class="folder-create" @submit.prevent="handleCreateFolder">
                <input v-model="newFolderName" type="text" placeholder="新建文件夹" />
                <button class="action-btn action-btn--primary" :disabled="creatingFolder">
                  {{ creatingFolder ? '创建中...' : '创建' }}
                </button>
                <button type="button" class="action-btn action-btn--secondary" @click="cancelCreateFolder">
                  取消
                </button>
              </form>
            </div>

            <template v-if="!selectedFolder">
              <div v-if="favoriteFolders.length" class="folder-grid">
                <button
                  v-for="folder in favoriteFolders"
                  :key="folder.id"
                  class="folder-tile"
                  @click="selectFolder(folder)"
                >
                  <span class="folder-tile__icon" aria-hidden="true">
                    <svg viewBox="0 0 92 82" fill="none">
                      <path class="folder-shadow" d="M14 32h64c5.8 0 10.5 4.7 10.5 10.5v25c0 5.8-4.7 10.5-10.5 10.5H14C8.2 78 3.5 73.3 3.5 67.5v-25C3.5 36.7 8.2 32 14 32Z" />
                      <path class="folder-back" d="M8 18.5C8 13.8 11.8 10 16.5 10h19.7c3 0 5.8 1.5 7.4 4.1l4.1 6.4h27.8C80.2 20.5 84 24.3 84 29v34.5c0 4.7-3.8 8.5-8.5 8.5h-59C11.8 72 8 68.2 8 63.5v-45Z" />
                      <path class="folder-front" d="M4 34c0-5.5 4.5-10 10-10h64c5.5 0 10 4.5 10 10v31c0 5.5-4.5 10-10 10H14C8.5 75 4 70.5 4 65V34Z" />
                      <path class="folder-highlight" d="M11.5 35.5c1.8-2.9 4.6-4.3 8.5-4.3h52.3c4.1 0 7.1 1.4 8.9 4.3" />
                    </svg>
                  </span>
                  <strong>{{ folder.name }}</strong>
                  <small>{{ folder.contractCount || 0 }} 个合约</small>
                </button>
              </div>

              <div v-else class="empty-panel">
                <strong>暂无收藏夹</strong>
                <p>创建一个文件夹，或在合约详情页收藏时自动生成默认文件夹。</p>
              </div>
            </template>

            <template v-else>
              <section class="folder-detail">
                <div v-if="selectedFolder" class="folder-detail__head">
                  <div>
                    <button class="folder-back-btn" @click="backToFolders">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="m15 18-6-6 6-6" />
                      </svg>
                      返回
                    </button>
                    <form
                      v-if="renamingFolderId === selectedFolder.id"
                      class="folder-rename-inline"
                      @submit.prevent="submitFolderRename"
                    >
                      <input
                        v-model="renamingFolderName"
                        type="text"
                        placeholder="文件夹名称"
                        @keydown.esc.prevent="cancelFolderRename"
                      />
                      <button type="submit" class="action-btn action-btn--primary">保存</button>
                      <button type="button" class="action-btn action-btn--secondary" @click="cancelFolderRename">取消</button>
                    </form>
                    <template v-else>
                      <h3>{{ selectedFolder.name }}</h3>
                      <p>共 {{ selectedFolder.contractCount || 0 }} 个合约</p>
                    </template>
                  </div>
                  <div class="folder-actions">
                    <button class="action-btn action-btn--secondary" @click="handleRenameFolder(selectedFolder)">
                      重命名
                    </button>
                    <button
                      v-if="!selectedFolder.isDefault"
                      class="action-btn action-btn--danger"
                      @click="handleDeleteFolder(selectedFolder)"
                    >
                      删除文件夹
                    </button>
                  </div>
                </div>

                <div v-if="folderContractsLoading" class="loading-state">
                  <span class="spinner"></span>
                  <span>加载合约...</span>
                </div>

                <div v-else-if="folderContracts.length" class="favorite-contract-list">
                  <article v-for="item in folderContracts" :key="item.id" class="favorite-contract-item">
                    <div class="favorite-contract-item__main">
                      <h4>{{ item.contractName || '未命名合约' }}</h4>
                      <p>{{ item.category || 'uncategorized' }} · {{ item.address }}</p>
                      <span>{{ item.description || '暂无描述' }}</span>
                    </div>
                    <div class="favorite-contract-item__actions">
                      <button class="action-btn action-btn--secondary" @click="handleCardClick(item)">查看</button>
                      <button
                        class="action-btn action-btn--danger"
                        :disabled="removingFavoriteId === item.id"
                        @click="handleRemoveFavorite(item)"
                      >
                        {{ removingFavoriteId === item.id ? '移除中...' : '移除' }}
                      </button>
                    </div>
                  </article>
                </div>

                <div v-else class="empty-panel">
                  <strong>当前文件夹暂无收藏</strong>
                  <p>收藏时可归入现有文件夹，也可创建新文件夹。</p>
                </div>
              </section>
            </template>
          </section>
        </template>
      </template>

      <template v-else-if="activeTab === 'contracts'">
        <section class="my-contract-panel">
          <div class="upload-panel__head">
            <div>
              <h2 class="section-title">我的合约</h2>
              <p>智能预审后的草稿和待收录申请。</p>
            </div>
            <div class="mini-tabs">
              <button :class="{ active: contractRecordTab === 'drafts' }" @click="switchContractRecordTab('drafts')">草稿</button>
              <button :class="{ active: contractRecordTab === 'submissions' }" @click="switchContractRecordTab('submissions')">待收录</button>
            </div>
          </div>

          <div v-if="contractRecordLoading" class="loading-state">
            <span class="spinner"></span>
            <span>加载中...</span>
          </div>
          <p v-else-if="contractRecordError" class="error-msg">{{ contractRecordError }}</p>

          <div v-else-if="activeContractRecords.length" class="contract-record-list">
            <article v-for="item in activeContractRecords" :key="item.id" class="contract-record-item">
              <div class="contract-record-main">
                <h3>{{ item.contractName || '未命名合约' }}</h3>
                <p>
                  风险 {{ riskText(item.riskLevel) }} · {{ item.issueCount || 0 }} 个问题 ·
                  {{ contractRecordTab === 'drafts' ? '保存' : '提交' }}于 {{ formatDateTime(item.updatedAt || item.createdAt) }}
                </p>
                <span v-if="contractRecordTab === 'submissions'" class="record-status">待收录</span>
              </div>
              <div class="contract-record-actions">
                <button class="action-btn action-btn--secondary" @click="viewContractRecord(item)">查看</button>
                <button
                  v-if="contractRecordTab === 'drafts'"
                  class="action-btn action-btn--secondary"
                  @click="continueEditContractRecord(item)"
                >
                  继续编辑
                </button>
                <button
                  v-if="contractRecordTab === 'drafts'"
                  class="action-btn action-btn--danger"
                  :disabled="recordDeletingId === item.id"
                  @click="deleteContractDraft(item)"
                >
                  {{ recordDeletingId === item.id ? '删除中...' : '删除' }}
                </button>
                <button
                  v-else
                  class="action-btn action-btn--danger"
                  :disabled="recordDeletingId === item.id"
                  @click="withdrawContractSubmission(item)"
                >
                  {{ recordDeletingId === item.id ? '撤回中...' : '撤回' }}
                </button>
              </div>
            </article>
          </div>

          <div v-else class="empty-panel">
            <strong>暂无合约记录</strong>
            <p>通过智能预审后可保存草稿或申请收录。</p>
          </div>
        </section>
      </template>
    </template>

    <div v-if="recordViewerOpen" class="profile-modal-backdrop" @click="closeRecordViewer">
      <section class="record-viewer-modal" @click.stop>
        <div class="profile-modal__head">
          <div>
            <p class="page-eyebrow">CONTRACT RECORD</p>
            <h2>{{ selectedRecord?.contractName || '未命名合约' }}</h2>
          </div>
          <button class="modal-close" @click="closeRecordViewer" aria-label="关闭查看弹窗">×</button>
        </div>
        <div class="record-viewer-summary">
          <span>{{ selectedRecord?.status === 'pending_review' ? '待收录' : '草稿' }}</span>
          <span>风险 {{ riskText(selectedRecord?.riskLevel) }}</span>
          <span>{{ selectedRecord?.issueCount || 0 }} 个问题</span>
        </div>
        <div class="record-viewer-body">
          <section>
            <h3>预审摘要</h3>
            <p>{{ selectedRecord?.precheckConclusion || '暂无摘要' }}</p>
            <ul v-if="selectedRecordIssues.length">
              <li v-for="issue in selectedRecordIssues" :key="issue.id || issue.title">
                <strong>{{ issue.title || '问题' }}</strong>
                <span>{{ issue.message || issue.suggestion || '需要进一步确认。' }}</span>
              </li>
            </ul>
          </section>
          <section>
            <h3>源码</h3>
            <pre class="record-source"><code>{{ selectedRecord?.sourceCode || '' }}</code></pre>
          </section>
        </div>
      </section>
    </div>

    <div v-if="recordConfirm.open" class="profile-confirm-backdrop" @click.self="cancelRecordConfirm">
      <section class="profile-confirm" role="dialog" aria-modal="true">
        <strong>{{ recordConfirm.title }}</strong>
        <p>{{ recordConfirm.message }}</p>
        <div>
          <button type="button" class="action-btn action-btn--secondary" @click="cancelRecordConfirm">取消</button>
          <button type="button" class="action-btn action-btn--danger" @click="confirmRecordAction">
            {{ recordConfirm.actionText }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="profileModalOpen" class="profile-modal-backdrop" @click="cancelEdit">
      <section class="profile-modal" @click.stop>
        <div class="profile-modal__head">
          <div>
            <p class="page-eyebrow">PROFILE SETTINGS</p>
            <h2>编辑资料</h2>
          </div>
          <button class="modal-close" @click="cancelEdit" aria-label="关闭编辑资料弹窗">×</button>
        </div>

        <div class="profile-modal__body">
          <section class="modal-section avatar-settings">
            <div class="modal-section__head">
              <h3>头像设置</h3>
              <p>上传本地头像并裁剪，或选择一个默认头像。</p>
            </div>

            <div class="avatar-editor">
              <div class="avatar-preview-wrap">
                <div class="avatar-preview">
                  <img v-if="avatarDraftPreview" :src="avatarDraftPreview" alt="" />
                  <span v-else>{{ profileInitial }}</span>
                </div>
                <input
                  ref="avatarFileInput"
                  class="sr-only"
                  type="file"
                  accept="image/png,image/jpeg,image/jpg,image/webp"
                  @change="handleAvatarFileChange"
                />
              </div>

              <div class="avatar-preset-panel">
                <p class="avatar-panel-title">头像选择</p>
                <div class="avatar-preset-grid">
                  <button
                    type="button"
                    class="avatar-preset avatar-preset--letter"
                    :class="{ active: avatarDraft.type === 'letter' }"
                    :disabled="avatarSaving"
                    @click="selectLetterAvatar"
                    aria-label="选择字母头像"
                  >
                    <span>{{ profileInitial }}</span>
                  </button>
                  <button
                    v-for="avatar in defaultAvatars"
                    :key="avatar.key"
                    type="button"
                    class="avatar-preset"
                    :class="{ active: avatarDraft.type === 'preset' && avatarDraft.presetKey === avatar.key }"
                    :disabled="avatarSaving"
                    @click="selectPresetAvatar(avatar)"
                    :aria-label="`选择${avatar.label}`"
                  >
                    <img :src="avatar.src" alt="" />
                  </button>
                  <div
                    v-for="avatar in uploadedAvatarOptions"
                    :key="avatar"
                    class="avatar-upload-option"
                  >
                    <button
                      type="button"
                      class="avatar-preset avatar-preset--upload"
                      :class="{ active: avatarDraft.type === 'upload' && avatarDraft.previewUrl === avatar }"
                      :disabled="avatarSaving || deletingAvatarUrl === avatar"
                      @click="selectUploadedAvatar(avatar)"
                      aria-label="选择上传头像"
                    >
                      <img :src="avatar" alt="" />
                    </button>
                    <button
                      type="button"
                      class="avatar-delete-btn"
                      :disabled="avatarSaving || deletingAvatarUrl === avatar"
                      @click="deleteUploadedAvatar(avatar)"
                      aria-label="删除上传头像"
                    >
                      ×
                    </button>
                  </div>
                  <button
                    type="button"
                    class="avatar-preset avatar-preset--add"
                    :disabled="avatarSaving"
                    @click="triggerAvatarUpload"
                    aria-label="上传头像"
                  >
                    <span>+</span>
                  </button>
                </div>
                <p v-if="avatarError" class="form-error form-error--block">{{ avatarError }}</p>
              </div>
            </div>

            <div v-if="cropperOpen" class="avatar-cropper">
              <div>
                <canvas
                  ref="cropCanvas"
                  class="avatar-cropper__canvas"
                  width="260"
                  height="260"
                  @pointerdown="startCropDrag"
                  @pointermove="moveCropDrag"
                  @pointerup="endCropDrag"
                  @pointerleave="endCropDrag"
                ></canvas>
              </div>
              <div class="avatar-cropper__tools">
                <label>
                  裁剪框大小
                  <input
                    v-model.number="cropState.cropSize"
                    type="range"
                    :min="CROP_MIN_SIZE"
                    :max="CROP_CANVAS_SIZE"
                    step="1"
                    @input="handleCropSizeChange"
                  />
                </label>
                <div class="avatar-cropper__actions">
                  <button type="button" class="action-btn action-btn--secondary" @click="cancelCrop">取消</button>
                  <button type="button" class="action-btn action-btn--primary" :disabled="avatarSaving" @click="confirmCrop">
                    {{ avatarSaving ? '保存中...' : '使用头像' }}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section class="modal-section account-profile-section">
            <div class="modal-section__head">
              <div>
                <h3>账户资料</h3>
                <p>账户身份与联系方式</p>
              </div>
            </div>

            <div class="profile-form">
              <div class="profile-field-row">
                <label for="profile-username">用户名</label>
                <input
                  id="profile-username"
                  :value="editForm.username"
                  class="profile-field-input profile-field-input--readonly"
                  type="text"
                  readonly
                />
                <div class="profile-field-actions" aria-hidden="true"></div>
              </div>

              <div class="profile-field-wrap">
                <div class="profile-field-row">
                  <label for="profile-nickname">昵称</label>
                  <input
                    id="profile-nickname"
                    ref="nicknameInput"
                    v-model="editForm.nickname"
                    class="profile-field-input"
                    :class="{
                      'profile-field-input--editing': editingField === 'nickname',
                      error: editErrors.nickname
                    }"
                    type="text"
                    placeholder="不超过 32 个字符"
                    :readonly="editingField !== 'nickname'"
                  />
                  <div class="profile-field-actions">
                    <template v-if="editingField === 'nickname'">
                      <button
                        type="button"
                        class="profile-link-btn"
                        :disabled="saving"
                        @click="cancelProfileFieldEdit('nickname')"
                      >
                        取消
                      </button>
                      <button
                        type="button"
                        class="profile-save-btn"
                        :disabled="saving"
                        @click="handleSaveProfile('nickname')"
                      >
                        <span v-if="saving" class="spinner spinner--light"></span>
                        {{ saving ? '保存中' : '保存' }}
                      </button>
                    </template>
                    <span v-if="savedProfileField === 'nickname'" class="profile-saved-hint">
                      已保存
                    </span>
                    <button
                      v-if="editingField !== 'nickname'"
                      type="button"
                      class="profile-link-btn"
                      :disabled="saving"
                      @click="startProfileFieldEdit('nickname')"
                    >
                      修改
                    </button>
                  </div>
                </div>
                <p v-if="editErrors.nickname" class="form-error">{{ editErrors.nickname }}</p>
              </div>

              <div class="profile-field-wrap">
                <div class="profile-field-row">
                  <label for="profile-email">邮箱</label>
                  <input
                    id="profile-email"
                    ref="emailInput"
                    v-model="editForm.email"
                    class="profile-field-input"
                    :class="{
                      'profile-field-input--editing': editingField === 'email',
                      error: editErrors.email
                    }"
                    type="email"
                    placeholder="user@example.com"
                    :readonly="editingField !== 'email'"
                  />
                  <div class="profile-field-actions">
                    <template v-if="editingField === 'email'">
                      <button
                        type="button"
                        class="profile-link-btn"
                        :disabled="saving"
                        @click="cancelProfileFieldEdit('email')"
                      >
                        取消
                      </button>
                      <button
                        type="button"
                        class="profile-save-btn"
                        :disabled="saving"
                        @click="handleSaveProfile('email')"
                      >
                        <span v-if="saving" class="spinner spinner--light"></span>
                        {{ saving ? '保存中' : '保存' }}
                      </button>
                    </template>
                    <span v-if="savedProfileField === 'email'" class="profile-saved-hint">
                      已保存
                    </span>
                    <button
                      v-if="editingField !== 'email'"
                      type="button"
                      class="profile-link-btn"
                      :disabled="saving"
                      @click="startProfileFieldEdit('email')"
                    >
                      修改
                    </button>
                  </div>
                </div>
                <p v-if="editErrors.email" class="form-error">{{ editErrors.email }}</p>
              </div>

              <p v-if="profileSaveError" class="form-error form-error--block">{{ profileSaveError }}</p>

            </div>
          </section>

          <section class="modal-section modal-section--security">
            <div class="modal-section__head">
              <h3>账户安全</h3>
              <p>更新登录密码</p>
            </div>

            <form class="pwd-form" @submit.prevent="handleChangePassword">
              <div class="form-group">
                <label>当前密码</label>
                <input
                  v-model="pwdForm.oldPassword"
                  class="form-input"
                  :class="{ error: pwdErrors.oldPassword }"
                  type="password"
                  placeholder="输入当前密码"
                />
                <p v-if="pwdErrors.oldPassword" class="form-error">{{ pwdErrors.oldPassword }}</p>
              </div>

              <div class="form-group">
                <label>新密码</label>
                <input
                  v-model="pwdForm.newPassword"
                  class="form-input"
                  :class="{ error: pwdErrors.newPassword }"
                  type="password"
                  placeholder="输入新密码"
                />
                <p v-if="pwdErrors.newPassword" class="form-error">{{ pwdErrors.newPassword }}</p>
              </div>

              <div class="form-group">
                <label>确认密码</label>
                <input
                  v-model="pwdForm.confirmPassword"
                  class="form-input"
                  :class="{ error: pwdErrors.confirmPassword }"
                  type="password"
                  placeholder="再次输入新密码"
                />
                <p v-if="pwdErrors.confirmPassword" class="form-error">{{ pwdErrors.confirmPassword }}</p>
              </div>

              <p v-if="pwdError" class="form-error form-error--block">{{ pwdError }}</p>
              <p v-if="pwdSuccess" class="success-msg">密码已修改成功</p>

              <button type="submit" class="action-btn action-btn--secondary pwd-submit" :disabled="pwdSaving">
                <span v-if="pwdSaving" class="spinner"></span>
                {{ pwdSaving ? '修改中...' : '更新密码' }}
              </button>
            </form>
          </section>
        </div>

      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { contractApi, userApi } from '@/api'
import avatarAsh from '@/assets/avatars/avatar-ash.png'
import avatarCyan from '@/assets/avatars/avatar-cyan.png'
import avatarGold from '@/assets/avatars/avatar-gold.png'
import avatarGraphite from '@/assets/avatars/avatar-graphite.png'
import avatarIndigo from '@/assets/avatars/avatar-indigo.png'
import avatarLavender from '@/assets/avatars/avatar-lavender.png'
import avatarMist from '@/assets/avatars/avatar-mist.png'
import avatarRose from '@/assets/avatars/avatar-rose.png'
import avatarSage from '@/assets/avatars/avatar-sage.png'
import avatarSteel from '@/assets/avatars/avatar-steel.png'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('favorites')
const pageLoading = ref(true)
const profileModalOpen = ref(false)
const editingField = ref(null)
const savedProfileField = ref(null)
const saving = ref(false)
const profileSaveError = ref('')

const editForm = reactive({ username: '', nickname: '', email: '' })
const editErrors = reactive({ username: '', nickname: '', email: '' })
const nicknameInput = ref(null)
const emailInput = ref(null)

const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdErrors = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdSaving = ref(false)
const pwdError = ref('')
const pwdSuccess = ref(false)

const favLoading = ref(false)
const favError = ref('')
const favLoaded = ref(false)
const favoriteFolders = ref([])
const selectedFolderId = ref(null)
const selectedFolder = ref(null)
const folderContracts = ref([])
const folderContractsLoading = ref(false)
const newFolderName = ref('')
const creatingFolder = ref(false)
const creatingFolderInline = ref(false)
const removingFavoriteId = ref(null)
const renamingFolderId = ref(null)
const renamingFolderName = ref('')

const contractRecordTab = ref('drafts')
const contractDrafts = ref([])
const contractSubmissions = ref([])
const contractRecordsLoaded = reactive({ drafts: false, submissions: false })
const contractRecordLoading = ref(false)
const contractRecordError = ref('')
const recordDeletingId = ref(null)
const recordViewerOpen = ref(false)
const selectedRecord = ref(null)
const recordConfirm = ref({
  open: false,
  type: '',
  title: '',
  message: '',
  actionText: '',
  payload: null
})

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const AVATAR_MAX_SIZE = 2 * 1024 * 1024
const CROP_CANVAS_SIZE = 260
const CROP_MIN_SIZE = 120
const AVATAR_OUTPUT_SIZE = 320
const CONTRACT_LAB_DRAFT_SOURCE_KEY = 'contractLabDraftSource'

const defaultAvatars = [
  { key: 'ash', label: '默认头像一', src: avatarAsh },
  { key: 'graphite', label: '默认头像二', src: avatarGraphite },
  { key: 'steel', label: '默认头像三', src: avatarSteel },
  { key: 'mist', label: '默认头像四', src: avatarMist },
  { key: 'sage', label: '默认头像五', src: avatarSage },
  { key: 'indigo', label: '默认头像六', src: avatarIndigo },
  { key: 'rose', label: '默认头像七', src: avatarRose },
  { key: 'gold', label: '默认头像八', src: avatarGold },
  { key: 'cyan', label: '默认头像九', src: avatarCyan },
  { key: 'lavender', label: '默认头像十', src: avatarLavender },
]

const avatarFileInput = ref(null)
const cropCanvas = ref(null)
const cropperOpen = ref(false)
const cropImageUrl = ref('')
const cropImage = ref(null)
const avatarError = ref('')
const avatarSaving = ref(false)
const deletingAvatarUrl = ref('')
const avatarChanged = ref(false)
const avatarLoadFailed = ref(false)
const uploadedAvatarCandidates = ref([])
const avatarDraft = reactive({
  type: 'letter',
  presetKey: '',
  previewUrl: '',
  imageData: '',
  avatarUrl: ''
})
const cropState = reactive({
  cropSize: 210,
  x: 0,
  y: 0,
  dragging: false,
  lastX: 0,
  lastY: 0
})

const displayName = computed(() => (
  userStore.profile?.nickname
  || userStore.profile?.username
  || '未命名用户'
))

const profileInitial = computed(() => {
  const name = displayName.value.trim()
  return name ? name.slice(0, 1).toUpperCase() : 'U'
})

const currentAvatarSrc = computed(() => {
  const profile = userStore.profile
  if (profile?.avatarType === 'upload' && profile.avatarUrl && !avatarLoadFailed.value) {
    return profile.avatarUrl
  }
  if (profile?.avatarType === 'preset' && profile.avatarPresetKey) {
    return findDefaultAvatar(profile.avatarPresetKey)?.src || ''
  }
  return ''
})

const avatarDraftPreview = computed(() => (
  avatarDraft.type === 'letter'
    ? ''
    : (avatarDraft.previewUrl || currentAvatarSrc.value)
))
const uploadedAvatarOptions = computed(() => {
  const profile = userStore.profile
  return uniqueAvatars([
    ...uploadedAvatarCandidates.value,
    ...parseAvatarHistory(profile?.avatarHistory),
    profile?.avatarType === 'upload' ? profile.avatarUrl : ''
  ])
})

const activeContractRecords = computed(() => (
  contractRecordTab.value === 'drafts' ? contractDrafts.value : contractSubmissions.value
))

const selectedRecordIssues = computed(() => {
  const issues = selectedRecord.value?.issues
  return Array.isArray(issues) ? issues : []
})

function findDefaultAvatar(key) {
  return defaultAvatars.find(avatar => avatar.key === key)
}

function resetAvatarDraftFromProfile() {
  const profile = userStore.profile
  if (profile?.avatarType === 'upload' && profile.avatarUrl) {
    avatarDraft.type = 'upload'
    avatarDraft.presetKey = ''
    avatarDraft.previewUrl = profile.avatarUrl
    avatarDraft.imageData = ''
    avatarDraft.avatarUrl = profile.avatarUrl
  } else if (profile?.avatarType === 'preset' && profile.avatarPresetKey) {
    const preset = findDefaultAvatar(profile.avatarPresetKey)
    if (preset) {
      avatarDraft.type = 'preset'
      avatarDraft.presetKey = preset.key
      avatarDraft.previewUrl = preset.src
      avatarDraft.imageData = ''
      avatarDraft.avatarUrl = ''
    } else {
      avatarDraft.type = 'letter'
      avatarDraft.presetKey = ''
      avatarDraft.previewUrl = ''
      avatarDraft.imageData = ''
      avatarDraft.avatarUrl = ''
    }
  } else {
    avatarDraft.type = 'letter'
    avatarDraft.presetKey = ''
    avatarDraft.previewUrl = ''
    avatarDraft.imageData = ''
    avatarDraft.avatarUrl = ''
  }
  avatarChanged.value = false
  avatarError.value = ''
  uploadedAvatarCandidates.value = []
  cancelCrop()
}

async function selectLetterAvatar() {
  avatarLoadFailed.value = false
  avatarDraft.type = 'letter'
  avatarDraft.presetKey = ''
  avatarDraft.previewUrl = ''
  avatarDraft.imageData = ''
  avatarDraft.avatarUrl = ''
  avatarChanged.value = true
  avatarError.value = ''
  await saveAvatarSelection({ avatarType: 'letter' })
}

async function selectPresetAvatar(avatar) {
  avatarLoadFailed.value = false
  avatarDraft.type = 'preset'
  avatarDraft.presetKey = avatar.key
  avatarDraft.previewUrl = avatar.src
  avatarDraft.imageData = ''
  avatarDraft.avatarUrl = ''
  avatarChanged.value = true
  avatarError.value = ''
  await saveAvatarSelection({
    avatarType: 'preset',
    avatarPresetKey: avatar.key
  })
}

async function selectUploadedAvatar(avatar) {
  if (!avatar) return
  avatarLoadFailed.value = false
  avatarDraft.type = 'upload'
  avatarDraft.presetKey = ''
  avatarDraft.previewUrl = avatar
  avatarDraft.imageData = avatar.startsWith('data:image/') ? avatar : ''
  avatarDraft.avatarUrl = avatar.startsWith('data:image/') ? '' : avatar
  avatarChanged.value = true
  avatarError.value = ''
  await saveAvatarSelection({
    avatarType: 'upload',
    ...(avatar.startsWith('data:image/')
      ? { avatarImageData: avatar }
      : { avatarUrl: avatar })
  })
}

function parseAvatarHistory(value) {
  if (Array.isArray(value)) {
    return value
  }
  if (typeof value !== 'string') {
    return []
  }
  return value.split(/\r?\n|,/).map(item => item.trim()).filter(Boolean)
}

function uniqueAvatars(list) {
  return [...new Set(list.filter(Boolean))]
}

async function saveAvatarSelection(payload) {
  if (avatarSaving.value) return
  avatarSaving.value = true
  avatarError.value = ''
  profileSaveError.value = ''
  try {
    await userStore.updateProfile(payload)
    resetAvatarDraftFromProfile()
  } catch (err) {
    avatarError.value = typeof err === 'string' ? err : '头像保存失败，请稍后重试'
  } finally {
    avatarSaving.value = false
  }
}

async function deleteUploadedAvatar(avatar) {
  if (!avatar || avatar.startsWith('data:image/') || deletingAvatarUrl.value) return
  deletingAvatarUrl.value = avatar
  avatarError.value = ''
  try {
    const res = await userApi.deleteUploadedAvatar({ avatarUrl: avatar })
    if (res.code !== 200) {
      throw typeof res.message === 'string' ? res.message : '删除头像失败'
    }
    await userStore.fetchProfile()
    uploadedAvatarCandidates.value = uploadedAvatarCandidates.value.filter(item => item !== avatar)
    resetAvatarDraftFromProfile()
  } catch (err) {
    avatarError.value = typeof err === 'string' ? err : '删除头像失败，请稍后重试'
  } finally {
    deletingAvatarUrl.value = ''
  }
}

function handleAvatarImageError() {
  avatarLoadFailed.value = true
}

function triggerAvatarUpload() {
  avatarFileInput.value?.click()
}

function handleAvatarFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  avatarError.value = ''
  if (!file) return
  if (!['image/png', 'image/jpeg', 'image/jpg', 'image/webp'].includes(file.type)) {
    avatarError.value = '仅支持 PNG、JPG、JPEG、WEBP 图片'
    return
  }
  if (file.size > AVATAR_MAX_SIZE) {
    avatarError.value = '头像大小不能超过 2MB'
    return
  }
  cancelCrop()
  const url = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    cropImage.value = image
    cropImageUrl.value = url
    cropState.cropSize = 210
    cropState.x = 0
    cropState.y = 0
    cropperOpen.value = true
    requestAnimationFrame(() => {
      clampCropOffset()
      drawCropCanvas()
    })
  }
  image.onerror = () => {
    URL.revokeObjectURL(url)
    avatarError.value = '图片读取失败，请更换文件'
  }
  image.src = url
}

function startCropDrag(event) {
  cropState.dragging = true
  cropState.lastX = event.clientX
  cropState.lastY = event.clientY
  event.currentTarget.setPointerCapture?.(event.pointerId)
}

function moveCropDrag(event) {
  if (!cropState.dragging) return
  cropState.x += event.clientX - cropState.lastX
  cropState.y += event.clientY - cropState.lastY
  cropState.lastX = event.clientX
  cropState.lastY = event.clientY
  clampCropOffset()
  drawCropCanvas()
}

function endCropDrag(event) {
  cropState.dragging = false
  event.currentTarget?.releasePointerCapture?.(event.pointerId)
}

function drawCropCanvas() {
  const canvas = cropCanvas.value
  const image = cropImage.value
  if (!canvas || !image) return
  clampCropOffset(canvas.width)
  const ctx = canvas.getContext('2d')
  const size = canvas.width
  ctx.clearRect(0, 0, size, size)
  ctx.fillStyle = '#f8fafc'
  ctx.fillRect(0, 0, size, size)
  drawAvatarImage(ctx, image, size)
  const cropRect = getCropRect(size)
  ctx.save()
  ctx.fillStyle = 'rgba(17, 24, 39, 0.46)'
  ctx.fillRect(0, 0, size, size)
  ctx.globalCompositeOperation = 'destination-out'
  drawRoundedRect(ctx, cropRect.x, cropRect.y, cropRect.size, cropRect.size, cropRect.radius)
  ctx.fill()
  ctx.restore()
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.94)'
  ctx.lineWidth = 2
  drawRoundedRect(ctx, cropRect.x, cropRect.y, cropRect.size, cropRect.size, cropRect.radius)
  ctx.stroke()
  ctx.strokeStyle = 'rgba(17, 24, 39, 0.42)'
  ctx.lineWidth = 1
  drawRoundedRect(ctx, cropRect.x + 3, cropRect.y + 3, cropRect.size - 6, cropRect.size - 6, Math.max(12, cropRect.radius - 2))
  ctx.stroke()
}

function handleCropSizeChange() {
  clampCropOffset()
  drawCropCanvas()
}

function clampCropOffset(size = CROP_CANVAS_SIZE) {
  const image = cropImage.value
  if (!image) return
  const ratio = size / CROP_CANVAS_SIZE
  const base = Math.max(size / image.width, size / image.height)
  const width = image.width * base
  const height = image.height * base
  const cropRect = getCropRect(size)
  const baseX = (size - width) / 2
  const baseY = (size - height) / 2
  const currentX = baseX + cropState.x * ratio
  const currentY = baseY + cropState.y * ratio
  const nextX = clamp(currentX, cropRect.x + cropRect.size - width, cropRect.x)
  const nextY = clamp(currentY, cropRect.y + cropRect.size - height, cropRect.y)
  cropState.x = (nextX - baseX) / ratio
  cropState.y = (nextY - baseY) / ratio
}

function clamp(value, min, max) {
  if (min > max) {
    return (min + max) / 2
  }
  return Math.min(max, Math.max(min, value))
}

function getAvatarDrawRect(size) {
  const image = cropImage.value
  if (!image) return { x: 0, y: 0, width: size, height: size }
  const base = Math.max(size / image.width, size / image.height)
  const offsetRatio = size / CROP_CANVAS_SIZE
  const width = image.width * base
  const height = image.height * base
  const x = (size - width) / 2 + cropState.x * offsetRatio
  const y = (size - height) / 2 + cropState.y * offsetRatio
  return { x, y, width, height }
}

function getCropRect(size) {
  const ratio = size / CROP_CANVAS_SIZE
  const cropSize = Math.min(size, Math.max(CROP_MIN_SIZE * ratio, cropState.cropSize * ratio))
  const x = (size - cropSize) / 2
  const y = (size - cropSize) / 2
  return {
    x,
    y,
    size: cropSize,
    radius: Math.max(14 * ratio, cropSize * 0.16)
  }
}

function drawRoundedRect(ctx, x, y, width, height, radius) {
  const r = Math.min(radius, width / 2, height / 2)
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.lineTo(x + width - r, y)
  ctx.quadraticCurveTo(x + width, y, x + width, y + r)
  ctx.lineTo(x + width, y + height - r)
  ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height)
  ctx.lineTo(x + r, y + height)
  ctx.quadraticCurveTo(x, y + height, x, y + height - r)
  ctx.lineTo(x, y + r)
  ctx.quadraticCurveTo(x, y, x + r, y)
  ctx.closePath()
}

function drawAvatarImage(ctx, image, size) {
  const { x, y, width, height } = getAvatarDrawRect(size)
  ctx.drawImage(image, x, y, width, height)
}

function confirmCrop() {
  const image = cropImage.value
  if (!image) return
  clampCropOffset()
  const output = document.createElement('canvas')
  output.width = AVATAR_OUTPUT_SIZE
  output.height = AVATAR_OUTPUT_SIZE
  const ctx = output.getContext('2d')
  const previewSize = cropCanvas.value?.width || CROP_CANVAS_SIZE
  const imageRect = getAvatarDrawRect(previewSize)
  const cropRect = getCropRect(previewSize)
  const ratio = AVATAR_OUTPUT_SIZE / cropRect.size
  ctx.clearRect(0, 0, AVATAR_OUTPUT_SIZE, AVATAR_OUTPUT_SIZE)
  ctx.fillStyle = '#f8fafc'
  ctx.fillRect(0, 0, AVATAR_OUTPUT_SIZE, AVATAR_OUTPUT_SIZE)
  ctx.drawImage(
    image,
    (imageRect.x - cropRect.x) * ratio,
    (imageRect.y - cropRect.y) * ratio,
    imageRect.width * ratio,
    imageRect.height * ratio
  )
  const imageData = output.toDataURL('image/png')
  uploadedAvatarCandidates.value = uniqueAvatars([imageData, ...uploadedAvatarCandidates.value])
  selectUploadedAvatar(imageData)
  avatarError.value = ''
  cancelCrop()
}

function cancelCrop() {
  cropperOpen.value = false
  cropImage.value = null
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
  }
  cropImageUrl.value = ''
  cropState.dragging = false
}

onMounted(async () => {
  try {
    await userStore.fetchProfile()
    await fetchFavorites()
  } finally {
    pageLoading.value = false
  }
})

async function switchToFavorites() {
  activeTab.value = 'favorites'
  if (!favLoaded.value) {
    await fetchFavorites()
  }
}

async function fetchFavorites() {
  favLoading.value = true
  favError.value = ''
  try {
    const res = await contractApi.getFavoriteFolders()
    if (!res.status) {
      throw new Error(res.message || '加载收藏夹失败')
    }
    favoriteFolders.value = res.data || []
    favLoaded.value = true
    if (selectedFolderId.value) {
      const nextFolder = favoriteFolders.value.find(folder => folder.id === selectedFolderId.value)
      selectedFolder.value = nextFolder || null
      await fetchFolderContracts(selectedFolderId.value)
    } else {
      selectedFolder.value = null
      folderContracts.value = []
    }
  } catch (err) {
    console.warn('加载收藏夹失败', err)
    favError.value = '加载失败，请稍后重试'
  } finally {
    favLoading.value = false
  }
}

async function fetchFolderContracts(folderId) {
  if (!folderId) return
  folderContractsLoading.value = true
  try {
    const res = await contractApi.getFavoriteFolderContracts(folderId)
    if (!res.status) {
      throw new Error(res.message || '加载文件夹合约失败')
    }
    folderContracts.value = res.data || []
  } catch (err) {
    console.warn('加载文件夹合约失败', err)
    favError.value = '加载失败，请稍后重试'
  } finally {
    folderContractsLoading.value = false
  }
}

async function selectFolder(folder) {
  selectedFolderId.value = folder.id
  selectedFolder.value = folder
  await fetchFolderContracts(folder.id)
}

function backToFolders() {
  selectedFolderId.value = null
  selectedFolder.value = null
  folderContracts.value = []
  cancelCreateFolder()
}

function startCreateFolder() {
  creatingFolderInline.value = true
  newFolderName.value = ''
}

function cancelCreateFolder() {
  creatingFolderInline.value = false
  newFolderName.value = ''
}

async function handleCreateFolder() {
  const name = newFolderName.value.trim()
  if (!name) return
  creatingFolder.value = true
  favError.value = ''
  try {
    const res = await contractApi.createFavoriteFolder({ name })
    if (!res.status) {
      throw new Error(res.message || '创建收藏夹失败')
    }
    newFolderName.value = ''
    creatingFolderInline.value = false
    selectedFolderId.value = null
    await fetchFavorites()
  } catch (err) {
    console.warn('创建收藏夹失败', err)
    favError.value = '创建失败，请重试'
  } finally {
    creatingFolder.value = false
  }
}

async function handleRenameFolder(folder) {
  renamingFolderId.value = folder.id
  renamingFolderName.value = folder.name || ''
}

function cancelFolderRename() {
  renamingFolderId.value = null
  renamingFolderName.value = ''
}

async function submitFolderRename() {
  if (!selectedFolder.value || renamingFolderId.value !== selectedFolder.value.id) return
  const name = renamingFolderName.value.trim()
  if (!name || name === selectedFolder.value.name) {
    cancelFolderRename()
    return
  }
  favError.value = ''
  try {
    const res = await contractApi.renameFavoriteFolder(selectedFolder.value.id, { name })
    if (!res.status) {
      throw new Error(res.message || '重命名收藏夹失败')
    }
    selectedFolder.value = {
      ...selectedFolder.value,
      name
    }
    selectedFolderId.value = selectedFolder.value.id
    cancelFolderRename()
    await fetchFavorites()
  } catch (err) {
    console.warn('重命名收藏夹失败', err)
    favError.value = '操作失败，请稍后重试'
  }
}

async function handleDeleteFolder(folder) {
  recordConfirm.value = {
    open: true,
    type: 'delete-folder',
    title: `删除「${folder.name}」？`,
    message: '该文件夹及其中收藏会从个人中心移除。',
    actionText: '删除',
    payload: folder
  }
}

async function performDeleteFolder(folder) {
  if (!folder) return
  favError.value = ''
  try {
    const res = await contractApi.deleteFavoriteFolder(folder.id)
    if (!res.status) {
      throw new Error(res.message || '删除收藏夹失败')
    }
    selectedFolderId.value = null
    await fetchFavorites()
  } catch (err) {
    console.warn('删除收藏夹失败', err)
    favError.value = '操作失败，请稍后重试'
  }
}

async function handleRemoveFavorite(item) {
  removingFavoriteId.value = item.id
  favError.value = ''
  try {
    const res = await contractApi.unfavoriteContract(item.id)
    if (!res.status) {
      throw new Error(res.message || '移除收藏失败')
    }
    folderContracts.value = folderContracts.value.filter(contract => contract.id !== item.id)
    favoriteFolders.value = favoriteFolders.value.map(folder => (
      folder.id === selectedFolderId.value
        ? { ...folder, contractCount: Math.max(0, (folder.contractCount || 1) - 1) }
        : folder
    ))
    selectedFolder.value = favoriteFolders.value.find(folder => folder.id === selectedFolderId.value) || null
  } catch (err) {
    console.warn('移除收藏失败', err)
    favError.value = '操作失败，请稍后重试'
  } finally {
    removingFavoriteId.value = null
  }
}

async function switchToMyContracts() {
  activeTab.value = 'contracts'
  await fetchContractRecords(contractRecordTab.value)
}

async function switchContractRecordTab(tab) {
  contractRecordTab.value = tab
  await fetchContractRecords(tab)
}

async function fetchContractRecords(tab = contractRecordTab.value) {
  contractRecordLoading.value = true
  contractRecordError.value = ''
  try {
    const res = tab === 'drafts'
      ? await contractApi.getDraftContracts()
      : await contractApi.getContractSubmissions()
    if (!res.status) {
      throw new Error(res.message || '加载记录失败')
    }
    if (tab === 'drafts') {
      contractDrafts.value = res.data || []
    } else {
      contractSubmissions.value = res.data || []
    }
    contractRecordsLoaded[tab] = true
  } catch (err) {
    console.warn('加载我的合约失败', err)
    contractRecordError.value = '数据加载失败，请稍后重试'
  } finally {
    contractRecordLoading.value = false
  }
}

function viewContractRecord(item) {
  selectedRecord.value = item
  recordViewerOpen.value = true
}

function closeRecordViewer() {
  recordViewerOpen.value = false
  selectedRecord.value = null
}

function continueEditContractRecord(item) {
  if (!item?.sourceCode) return
  sessionStorage.setItem(CONTRACT_LAB_DRAFT_SOURCE_KEY, JSON.stringify({
    source: 'profile-draft',
    contractName: item.contractName || 'DraftContract',
    fileName: `${sanitizeFileName(item.contractName || 'DraftContract')}.sol`,
    sourceCode: item.sourceCode,
    recordId: item.id
  }))
  router.push('/contract-lab')
}

async function deleteContractDraft(item) {
  if (!item?.id) return
  recordConfirm.value = {
    open: true,
    type: 'delete-draft',
    title: `删除「${item.contractName || '未命名合约'}」？`,
    message: '删除后该草稿将从个人中心移除。',
    actionText: '删除',
    payload: item
  }
}

async function performDeleteContractDraft(item) {
  recordDeletingId.value = item.id
  contractRecordError.value = ''
  try {
    const res = await contractApi.deleteDraftContract(item.id)
    if (!res.status) {
      throw new Error(res.message || '删除草稿失败')
    }
    contractDrafts.value = contractDrafts.value.filter(record => record.id !== item.id)
  } catch (err) {
    console.warn('删除草稿失败', err)
    contractRecordError.value = '操作失败，请稍后重试'
  } finally {
    recordDeletingId.value = null
  }
}

async function withdrawContractSubmission(item) {
  if (!item?.id) return
  recordConfirm.value = {
    open: true,
    type: 'withdraw-submission',
    title: `撤回「${item.contractName || '未命名合约'}」？`,
    message: '撤回后该待收录申请将从列表中移除。',
    actionText: '撤回',
    payload: item
  }
}

async function performWithdrawContractSubmission(item) {
  recordDeletingId.value = item.id
  contractRecordError.value = ''
  try {
    const res = await contractApi.withdrawContractSubmission(item.id)
    if (!res.status) {
      throw new Error(res.message || '撤回申请失败')
    }
    contractSubmissions.value = contractSubmissions.value.filter(record => record.id !== item.id)
  } catch (err) {
    console.warn('撤回申请失败', err)
    contractRecordError.value = '操作失败，请稍后重试'
  } finally {
    recordDeletingId.value = null
  }
}

function cancelRecordConfirm() {
  recordConfirm.value = { open: false, type: '', title: '', message: '', actionText: '', payload: null }
}

function confirmRecordAction() {
  const pending = recordConfirm.value
  cancelRecordConfirm()
  if (pending.type === 'delete-draft') {
    performDeleteContractDraft(pending.payload)
  } else if (pending.type === 'withdraw-submission') {
    performWithdrawContractSubmission(pending.payload)
  } else if (pending.type === 'delete-folder') {
    performDeleteFolder(pending.payload)
  }
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  return date.toLocaleString()
}

function riskText(level) {
  const normalized = String(level || '').toLowerCase()
  if (normalized === 'high') return '高'
  if (normalized === 'medium') return '中'
  if (normalized === 'low') return '低'
  return '未知'
}

function sanitizeFileName(name) {
  return String(name || 'Contract')
    .replace(/\.(sol|txt)$/i, '')
    .replace(/[^\w\u4e00-\u9fa5-]/g, '') || 'Contract'
}

function handleCardClick(item) {
  router.push(`/contract/${item.id}`)
}

function openEditProfile() {
  startEdit()
}

function startEdit() {
  editForm.username = userStore.profile?.username || ''
  editForm.nickname = userStore.profile?.nickname || ''
  editForm.email = userStore.profile?.email || ''
  editingField.value = null
  resetAvatarDraftFromProfile()
  editErrors.username = ''
  editErrors.nickname = ''
  editErrors.email = ''
  profileSaveError.value = ''
  savedProfileField.value = null
  profileModalOpen.value = true
}

function cancelEdit() {
  profileModalOpen.value = false
  editingField.value = null
  savedProfileField.value = null
  profileSaveError.value = ''
  editForm.nickname = userStore.profile?.nickname || ''
  editForm.email = userStore.profile?.email || ''
  editErrors.nickname = ''
  editErrors.email = ''
  resetAvatarDraftFromProfile()
}

async function startProfileFieldEdit(field) {
  if (!['nickname', 'email'].includes(field) || saving.value) return
  if (editingField.value && editingField.value !== field) {
    cancelProfileFieldEdit(editingField.value)
  }
  editingField.value = field
  profileSaveError.value = ''
  savedProfileField.value = null
  editErrors[field] = ''
  await nextTick()
  const input = field === 'nickname' ? nicknameInput.value : emailInput.value
  input?.focus()
  input?.select()
}

function cancelProfileFieldEdit(field = editingField.value) {
  if (!field) return
  if (field === 'nickname') {
    editForm.nickname = userStore.profile?.nickname || ''
    editErrors.nickname = ''
  } else if (field === 'email') {
    editForm.email = userStore.profile?.email || ''
    editErrors.email = ''
  }
  profileSaveError.value = ''
  savedProfileField.value = null
  if (editingField.value === field) {
    editingField.value = null
  }
}

function validateProfileField(field) {
  if (field === 'nickname') {
    editErrors.nickname = !editForm.nickname.trim()
      ? '请输入昵称'
      : editForm.nickname.trim().length > 32
        ? '昵称不超过 32 个字符'
        : ''
    return !editErrors.nickname
  }

  if (field === 'email') {
    editErrors.email = !editForm.email.trim()
      ? '请输入邮箱'
      : editForm.email.trim().length > 64
        ? '邮箱不超过 64 个字符'
      : !EMAIL_RE.test(editForm.email)
        ? '邮箱格式不正确'
        : ''
    return !editErrors.email
  }

  return false
}

async function handleSaveProfile(field = editingField.value) {
  if (!field || editingField.value !== field) return
  if (!validateProfileField(field)) return
  saving.value = true
  profileSaveError.value = ''
  try {
    const payload = field === 'nickname'
      ? { nickname: editForm.nickname.trim() }
      : { email: editForm.email.trim() }
    await userStore.updateProfile(payload)
    editForm.nickname = userStore.profile?.nickname || ''
    editForm.email = userStore.profile?.email || ''
    editingField.value = null
    resetAvatarDraftFromProfile()
    savedProfileField.value = field
    setTimeout(() => {
      if (savedProfileField.value === field) {
        savedProfileField.value = null
      }
    }, 1800)
  } catch (err) {
    profileSaveError.value = typeof err === 'string' ? err : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

function validatePwd() {
  pwdErrors.oldPassword = pwdForm.oldPassword ? '' : '请输入当前密码'
  pwdErrors.newPassword = pwdForm.newPassword ? '' : '请输入新密码'
  pwdErrors.confirmPassword = !pwdForm.confirmPassword
    ? '请确认新密码'
    : pwdForm.confirmPassword !== pwdForm.newPassword
      ? '两次输入的密码不一致'
      : ''

  return !pwdErrors.oldPassword && !pwdErrors.newPassword && !pwdErrors.confirmPassword
}

async function handleChangePassword() {
  if (!validatePwd()) return
  pwdSaving.value = true
  pwdError.value = ''
  pwdSuccess.value = false
  try {
    await userStore.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    pwdSuccess.value = true
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    setTimeout(() => {
      pwdSuccess.value = false
    }, 3000)
  } catch (err) {
    pwdError.value = typeof err === 'string' ? err : '修改失败，请确认当前密码是否正确'
  } finally {
    pwdSaving.value = false
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 1080px;
}

.page-header {
  margin-bottom: 18px;
}

.page-eyebrow {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #9ca3af;
  text-transform: uppercase;
}

.page-title {
  margin: 0;
  font-size: 36px;
  line-height: 1.02;
  font-weight: 700;
  letter-spacing: -0.045em;
  color: #111827;
}

.page-subtitle {
  margin: 12px 0 0;
  max-width: 620px;
  font-size: 15px;
  line-height: 1.8;
  color: #6b7280;
}

.account-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  margin: 0 0 18px;
  padding: 18px;
  border: 1px solid #e7eaf0;
  border-radius: 26px;
  background:
    linear-gradient(135deg, #ffffff 0%, #f8f9fb 100%);
  box-shadow:
    0 14px 36px rgba(15, 23, 42, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.88);
}

.account-avatar {
  width: 64px;
  aspect-ratio: 1 / 1;
  border-radius: 22px;
  background: #f3f4f6;
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.04em;
  box-shadow:
    0 0 0 1px rgba(17, 24, 39, 0.06),
    0 16px 28px rgba(15, 23, 42, 0.10);
}

.account-avatar img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.account-avatar span {
  width: 100%;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #111827;
}

.account-identity {
  min-width: 0;
}

.account-kicker {
  margin: 0 0 5px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.account-identity h2 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  line-height: 1.15;
  font-weight: 800;
  letter-spacing: -0.04em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-identity p:last-child {
  margin: 7px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.account-edit-btn {
  height: 42px;
  border-radius: 14px;
}

.tabs {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 4px;
  padding: 5px;
  margin-bottom: 18px;
  border-radius: 18px;
  background: #f5f6f8;
  border: 1px solid #e7eaf0;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
}

.tab-btn {
  min-width: 0;
  height: 42px;
  padding: 0 20px;
  background: transparent;
  border: none;
  border-radius: 14px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  letter-spacing: -0.01em;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.tab-btn:hover {
  color: #111827;
}

.tab-btn.active {
  background: linear-gradient(180deg, #ffffff 0%, #fbfbfc 100%);
  color: #111827;
  box-shadow:
    0 8px 18px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.section-card {
  position: relative;
  background: linear-gradient(180deg, #ffffff 0%, #fcfcfd 100%);
  border: 1px solid #e7eaf0;
  border-radius: 24px;
  padding: 18px;
  box-shadow:
    0 10px 24px rgba(15, 23, 42, 0.04),
    inset 0 1px 0 rgba(255,255,255,0.9);
  overflow: hidden;
  margin-bottom: 0;
}

.section-card::before {
  content: "";
  position: absolute;
  left: 20px;
  right: 20px;
  top: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    rgba(255,255,255,0),
    rgba(255,255,255,0.95),
    rgba(255,255,255,0)
  );
  pointer-events: none;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.section-header--single {
  justify-content: flex-start;
}

.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #111827;
}

.section-note {
  margin: 6px 0 0;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.6;
}

.edit-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1px solid #edf0f4;
  border-radius: 18px;
  overflow: hidden;
  background: #ffffff;
}

.info-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  min-height: 58px;
  padding: 10px 14px;
  border-bottom: 1px solid #f0f2f5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 12px;
  color: #98a2b3;
  letter-spacing: 0.04em;
  font-weight: 700;
  white-space: nowrap;
}

.info-value {
  font-size: 14px;
  color: #111827;
  line-height: 1.75;
  word-break: break-word;
  font-weight: 650;
}

.info-input-wrap {
  width: 100%;
}

.pwd-form {
  display: flex;
  flex-direction: column;
  gap: 13px;
  max-width: 100%;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.form-group label {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.form-input {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid #e5e7eb;
  background: #fcfcfd;
  color: #111827;
  font-size: 14px;
  outline: none;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
  box-sizing: border-box;
}

.form-input::placeholder {
  color: #9ca3af;
}

.form-input:focus {
  border-color: #cfd6de;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.04);
}

.form-input.error {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.06);
}

.form-error {
  font-size: 12px;
  line-height: 1.4;
  color: #ef4444;
}

.form-error--block {
  margin-top: 12px;
}

.success-msg {
  margin-top: 12px;
  font-size: 13px;
  color: #111827;
  font-weight: 700;
}

.action-btn {
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.action-btn--primary {
  border: none;
  background: linear-gradient(180deg, #111827 0%, #0f172a 100%);
  color: #ffffff;
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.08),
    0 8px 18px rgba(15, 23, 42, 0.10);
}

.action-btn--primary:hover:not(:disabled) {
  opacity: 0.97;
  transform: translateY(-1px);
}

.action-btn--primary:disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.action-btn--secondary {
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #6b7280;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
}

.action-btn--secondary:hover {
  background: #f8f9fb;
  border-color: #d9dee5;
  color: #111827;
  transform: translateY(-1px);
}

.action-btn--danger {
  border: 1px solid #dcdfe5;
  background: #f8f9fb;
  color: #111827;
}

.action-btn--danger:hover:not(:disabled) {
  background: #111827;
  border-color: #111827;
  color: #ffffff;
  transform: translateY(-1px);
}

.action-btn--danger:disabled {
  opacity: 0.64;
  cursor: not-allowed;
}

.pwd-submit {
  margin-top: 6px;
  width: fit-content;
  min-width: 112px;
}

.profile-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 260;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.30);
  backdrop-filter: blur(7px);
}

.profile-confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 280;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.24);
  backdrop-filter: blur(6px);
}

.profile-confirm {
  width: min(380px, 100%);
  padding: 18px;
  border: 1px solid #dfe3e8;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.20);
  display: grid;
  gap: 12px;
}

.profile-confirm strong {
  color: #111827;
  font-size: 15px;
  font-weight: 850;
}

.profile-confirm p {
  margin: 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.65;
}

.profile-confirm div {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
}

.profile-modal {
  width: min(820px, 100%);
  max-height: calc(100vh - 64px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(229, 231, 235, 0.96);
  border-radius: 28px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfbfc 100%);
  box-shadow:
    0 28px 80px rgba(15, 23, 42, 0.24),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
}

.profile-modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 24px 24px 18px;
  border-bottom: 1px solid #edf0f4;
}

.profile-modal__head h2 {
  margin: 0;
  color: #111827;
  font-size: 26px;
  line-height: 1.12;
  letter-spacing: -0.04em;
}

.modal-close {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #6b7280;
  font-size: 26px;
  line-height: 1;
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.modal-close:hover {
  color: #111827;
  background: #f8f9fb;
  border-color: #d9dee5;
  transform: translateY(-1px);
}

.profile-modal__body {
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal-section {
  border: 1px solid #e7eaf0;
  border-radius: 22px;
  background: #ffffff;
  padding: 18px;
}

.modal-section--security {
  background: #fcfcfd;
}

.modal-section__head {
  margin-bottom: 16px;
}

.modal-section__head h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.modal-section__head p {
  margin: 6px 0 0;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.6;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.avatar-settings {
  background:
    linear-gradient(180deg, #ffffff 0%, #fcfcfd 100%);
}

.avatar-editor {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.avatar-preview-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #edf0f4;
  border-radius: 18px;
  background: #f8f9fb;
}

.avatar-preview {
  width: 104px;
  aspect-ratio: 1 / 1;
  border-radius: 30px;
  overflow: hidden;
  background: #111827;
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  font-weight: 800;
  box-shadow:
    0 0 0 1px rgba(17, 24, 39, 0.08),
    0 18px 36px rgba(15, 23, 42, 0.14);
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.avatar-preset-panel {
  min-width: 0;
}

.avatar-panel-title {
  margin: 0 0 10px;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.avatar-preset-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 10px;
}

.avatar-upload-option {
  position: relative;
  aspect-ratio: 1;
  min-width: 0;
}

.avatar-preset {
  width: 100%;
  aspect-ratio: 1;
  border: 1px solid #e5e7eb;
  border-radius: 22px;
  background: #ffffff;
  cursor: pointer;
  overflow: hidden;
  padding: 0;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.avatar-preset:disabled {
  cursor: default;
  opacity: 0.72;
}

.avatar-preset img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.avatar-preset--letter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #111827;
  color: #ffffff;
}

.avatar-preset--letter span {
  font-size: 28px;
  font-weight: 850;
  line-height: 1;
}

.avatar-delete-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  border: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.94);
  color: #111827;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
  opacity: 0;
  transform: translateY(2px);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease,
    background 0.18s ease;
}

.avatar-upload-option:hover .avatar-delete-btn,
.avatar-delete-btn:focus-visible {
  opacity: 1;
  transform: translateY(0);
}

.avatar-delete-btn:hover {
  background: #111827;
  color: #ffffff;
}

.avatar-delete-btn:disabled {
  cursor: default;
  opacity: 0.45;
}

.avatar-preset--add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(180deg, #ffffff 0%, #f6f7f9 100%);
  border-style: dashed;
}

.avatar-preset--add span {
  color: #667085;
  font-size: 32px;
  font-weight: 500;
  line-height: 1;
  transform: translateY(-1px);
}

.avatar-preset--add:hover span {
  color: #111827;
}

.avatar-preset:hover,
.avatar-preset.active {
  border-color: #111827;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.10);
  transform: translateY(-1px);
}

.avatar-cropper {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #edf0f4;
  border-radius: 18px;
  background: #f8f9fb;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.avatar-cropper__canvas {
  width: 260px;
  height: 260px;
  border-radius: 24px;
  background: #ffffff;
  cursor: grab;
  touch-action: none;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.avatar-cropper__canvas:active {
  cursor: grabbing;
}

.avatar-cropper__tools {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.avatar-cropper__tools label {
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.avatar-cropper__tools input[type='range'] {
  accent-color: #111827;
}

.avatar-cropper__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.profile-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-field-wrap {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.profile-field-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 160px;
  align-items: center;
  gap: 18px;
}

.profile-field-row label {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.profile-field-input {
  width: 100%;
  min-width: 0;
  height: 42px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid #e8ebf0;
  background: #f7f8fa;
  color: #111827;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  cursor: default;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.profile-field-input--readonly {
  color: #374151;
}

.profile-field-input--editing {
  background: #ffffff;
  border-color: #c7ced8;
  cursor: text;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.04);
}

.profile-field-input.error {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.06);
}

.profile-field-actions {
  width: 160px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.profile-link-btn,
.profile-save-btn {
  height: 34px;
  min-width: 64px;
  padding: 0 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.profile-link-btn {
  border: 1px solid #e3e7ee;
  background: linear-gradient(180deg, #ffffff 0%, #f7f8fa 100%);
  color: #475467;
  box-shadow:
    0 7px 14px rgba(15, 23, 42, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.profile-link-btn:hover:not(:disabled) {
  border-color: #d2d8e2;
  background: #ffffff;
  color: #111827;
  box-shadow:
    0 10px 20px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
}

.profile-save-btn {
  border: 1px solid #111827;
  background: #111827;
  color: #ffffff;
  box-shadow: 0 8px 16px rgba(15, 23, 42, 0.12);
}

.profile-save-btn:hover:not(:disabled) {
  background: #0f172a;
  border-color: #0f172a;
}

.profile-link-btn:disabled,
.profile-save-btn:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.profile-saved-hint {
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.result-count {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 16px;
  letter-spacing: 0.01em;
}

.result-count strong {
  color: #111827;
  font-weight: 700;
}

.loading-state {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #6b7280;
  padding: 8px 2px;
}

.error-msg {
  color: #ef4444;
  font-size: 14px;
  margin-bottom: 12px;
}

.favorite-workspace {
  background: #ffffff;
  border: 1px solid #e7eaf0;
  border-radius: 24px;
  padding: 20px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.favorite-workspace__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid #edf0f4;
}

.favorite-workspace__head p {
  margin: 7px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
}

.folder-create {
  display: flex;
  gap: 8px;
  flex: 0 0 430px;
}

.folder-create input {
  min-width: 0;
  flex: 1;
  height: 38px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 0 12px;
  background: #fcfcfd;
  color: #111827;
  outline: none;
  font-size: 13px;
}

.folder-create input:focus {
  border-color: #cfd6de;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.04);
}

.folder-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 18px;
}

.folder-tile {
  min-width: 0;
  min-height: 142px;
  padding: 14px 10px 12px;
  border: 1px solid transparent;
  border-radius: 18px;
  background: transparent;
  color: #111827;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  cursor: pointer;
  text-align: center;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.folder-tile:hover {
  transform: translateY(-2px);
  border-color: #edf0f4;
  background: #fcfcfd;
}

.folder-tile__icon {
  width: 88px;
  height: 78px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 12px 16px rgba(17, 24, 39, 0.10));
}

.folder-tile__icon svg {
  width: 88px;
  height: 78px;
  overflow: visible;
}

.folder-shadow {
  fill: rgba(17, 24, 39, 0.08);
}

.folder-back {
  fill: #e9edf3;
  stroke: #cfd6de;
  stroke-width: 1.4;
}

.folder-front {
  fill: #f7f8fa;
  stroke: #cfd6de;
  stroke-width: 1.4;
}

.folder-highlight {
  stroke: #ffffff;
  stroke-width: 2;
  stroke-linecap: round;
  opacity: 0.9;
}

.folder-tile:hover .folder-back {
  fill: #e4e8ef;
}

.folder-tile:hover .folder-front {
  fill: #ffffff;
}

.folder-tile strong {
  width: 100%;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-tile small {
  color: #9ca3af;
  font-size: 12px;
  font-weight: 600;
}

.folder-detail {
  min-width: 0;
  border: 1px solid #edf0f4;
  border-radius: 18px;
  background: #fcfcfd;
  padding: 16px;
}

.folder-back-btn {
  height: 40px;
  margin: 0 0 12px;
  padding: 0 14px 0 12px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background:
    linear-gradient(180deg, #ffffff 0%, #f8f9fb 100%);
  color: #6b7280;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.9),
    0 8px 18px rgba(15, 23, 42, 0.05);
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.folder-back-btn svg {
  width: 16px;
  height: 16px;
}

.folder-back-btn:hover {
  border-color: #d9dee5;
  color: #111827;
  transform: translateY(-1px);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.95),
    0 12px 24px rgba(15, 23, 42, 0.08);
}

.folder-rename-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.folder-rename-inline input {
  min-width: 220px;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #dfe3e8;
  border-radius: 12px;
  background: #ffffff;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  outline: none;
}

.folder-rename-inline input:focus {
  border-color: #111827;
  box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.06);
}

.folder-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.folder-detail__head h3 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.folder-detail__head p {
  margin: 6px 0 0;
  color: #9ca3af;
  font-size: 12px;
}

.folder-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.favorite-contract-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.favorite-contract-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 14px;
  border: 1px solid #edf0f4;
  border-radius: 16px;
  background: #ffffff;
}

.favorite-contract-item__main {
  min-width: 0;
}

.favorite-contract-item h4 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.favorite-contract-item p {
  margin: 7px 0 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.favorite-contract-item span {
  display: -webkit-box;
  margin-top: 7px;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.favorite-contract-item__actions {
  display: flex;
  gap: 8px;
}

.upload-panel {
  background: #ffffff;
  border: 1px solid #e7eaf0;
  border-radius: 24px;
  padding: 20px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.upload-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #edf0f4;
}

.upload-panel__head p {
  margin: 7px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
}

.upload-panel__head span {
  flex: 0 0 auto;
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  background: #f8f9fb;
}

.upload-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.my-contract-panel {
  background: #ffffff;
  border: 1px solid #e7eaf0;
  border-radius: 24px;
  padding: 20px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.mini-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #f8fafc;
}

.mini-tabs button {
  height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.mini-tabs button.active {
  background: #ffffff;
  color: #111827;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.08);
}

.contract-record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.contract-record-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border: 1px solid #edf0f4;
  border-radius: 18px;
  background: #fcfcfd;
}

.contract-record-main {
  min-width: 0;
}

.contract-record-main h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.contract-record-main p {
  margin: 7px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.7;
}

.record-status {
  display: inline-flex;
  margin-top: 8px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #667085;
  font-size: 11px;
  font-weight: 800;
}

.contract-record-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.upload-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border: 1px solid #edf0f4;
  border-radius: 18px;
  background: #fcfcfd;
}

.upload-item__main {
  min-width: 0;
}

.upload-item h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.upload-item__meta {
  margin: 7px 0 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.upload-item__desc {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.upload-item__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.empty-panel {
  padding: 36px 16px;
  border: 1px dashed #d9dee5;
  border-radius: 18px;
  text-align: center;
  background: #fcfcfd;
}

.empty-panel strong {
  display: block;
  color: #111827;
  font-size: 15px;
}

.empty-panel p {
  margin: 8px auto 0;
  max-width: 360px;
  color: #9ca3af;
  font-size: 13px;
  line-height: 1.7;
}

.record-viewer-modal {
  width: min(960px, calc(100vw - 40px));
  max-height: calc(100vh - 52px);
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 26px;
  background: #ffffff;
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.22);
  display: flex;
  flex-direction: column;
}

.record-viewer-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 0 22px 16px;
  border-bottom: 1px solid #edf0f4;
}

.record-viewer-summary span {
  padding: 5px 9px;
  border-radius: 999px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.record-viewer-body {
  min-height: 0;
  overflow: auto;
  padding: 20px 22px 24px;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
}

.record-viewer-body h3 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 14px;
  font-weight: 850;
}

.record-viewer-body p,
.record-viewer-body li {
  color: #475467;
  font-size: 13px;
  line-height: 1.75;
}

.record-viewer-body ul {
  margin: 12px 0 0;
  padding-left: 18px;
}

.record-viewer-body li + li {
  margin-top: 8px;
}

.record-viewer-body li strong,
.record-viewer-body li span {
  display: block;
}

.record-source {
  max-height: 520px;
  overflow: auto;
  margin: 0;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fbfcfd;
  color: #1f2937;
  font-size: 12px;
  line-height: 1.75;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(17, 24, 39, 0.18);
  border-top-color: #111827;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.spinner--light {
  border-color: rgba(255,255,255,0.32);
  border-top-color: #ffffff;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .page-title {
    font-size: 30px;
  }

  .page-subtitle {
    font-size: 14px;
  }

  .account-hero {
    grid-template-columns: auto minmax(0, 1fr);
    gap: 14px;
    padding: 16px;
    border-radius: 22px;
  }

  .account-avatar {
    width: 56px;
    height: 56px;
    font-size: 22px;
  }

  .account-edit-btn {
    grid-column: 1 / -1;
    width: 100%;
  }

  .tabs {
    width: 100%;
  }

  .tab-btn {
    flex: 1;
    padding: 0 10px;
  }

  .profile-modal-backdrop {
    padding: 12px;
    align-items: flex-end;
  }

  .profile-modal {
    max-height: calc(100vh - 24px);
    border-radius: 24px;
  }

  .profile-modal__head {
    padding: 20px 18px 16px;
  }

  .profile-modal__body {
    padding: 16px 18px 20px;
  }

  .avatar-editor,
  .avatar-cropper {
    grid-template-columns: 1fr;
  }

  .avatar-preset-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .avatar-cropper {
    justify-items: center;
  }

  .avatar-cropper__tools {
    width: 100%;
  }

  .profile-field-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .profile-field-actions {
    width: 100%;
    min-width: 0;
    justify-content: flex-start;
  }

  .profile-link-btn,
  .profile-save-btn {
    min-width: 68px;
  }

  .info-row {
    grid-template-columns: 1fr;
    align-items: start;
    gap: 7px;
    padding: 12px;
  }

  .info-value {
    line-height: 1.55;
  }

  .pwd-form {
    max-width: 100%;
  }

  .section-card {
    padding: 18px;
    border-radius: 20px;
  }

  .edit-actions {
    width: 100%;
  }

  .edit-actions .action-btn {
    flex: 1;
  }

  .pwd-submit {
    width: 100%;
  }

  .favorite-workspace__head,
  .folder-detail__head,
  .favorite-contract-item {
    grid-template-columns: 1fr;
  }

  .folder-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .folder-tile {
    min-height: 126px;
  }

  .favorite-workspace__head,
  .folder-detail__head {
    display: grid;
  }

  .folder-create {
    width: 100%;
    flex-basis: auto;
  }

  .favorite-contract-item__actions,
  .folder-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .favorite-contract-item__actions .action-btn,
  .folder-actions .action-btn {
    flex: 1;
  }

  .upload-panel__head,
  .upload-item {
    grid-template-columns: 1fr;
  }

  .upload-panel__head {
    display: grid;
  }

  .upload-item__actions {
    width: 100%;
  }

  .upload-item__actions .action-btn {
    flex: 1;
  }
}
</style>
