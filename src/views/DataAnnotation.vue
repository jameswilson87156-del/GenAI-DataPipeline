<template>
  <div class="annotation-shell">
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>
    <div class="ambient-grid"></div>

    <section class="annotation-page">
      <header class="hero-panel glass-card">
        <div class="hero-main">
          <div class="hero-copy">
            <p class="eyebrow">AIGC DATA ASSET WORKBENCH</p>
            <h1 class="hero-title">专家标注工作台</h1>
            <p class="hero-subtitle">
              让 AI 预标注、专家确认与任务流转在一个高强度流水线界面中闭环完成。
            </p>
          </div>

          <div class="hero-controls">
            <el-input
              v-model="taskIdInput"
              class="task-input"
              placeholder="输入任务 ID 后加载"
              clearable
              @keyup.enter="applyTaskId"
            >
              <template #prepend>
                <span class="task-label">TASK</span>
              </template>
              <template #append>
                <el-button :loading="pageLoading" @click="applyTaskId">
                  加载任务
                </el-button>
              </template>
            </el-input>

            <div class="shortcut-hint">
              <span>快捷键支持：</span>
              <kbd>Alt + A</kbd>
              <span>采纳 AI</span>
              <kbd>Ctrl + Enter</kbd>
              <span>提交并下一条</span>
            </div>
          </div>
        </div>

        <div class="metric-grid">
          <article
            v-for="card in metrics"
            :key="card.key"
            class="metric-card"
            :class="`metric-${card.key}`"
          >
            <div class="metric-meta">
              <span class="metric-label">{{ card.label }}</span>
              <span class="metric-trend">{{ card.trend }}</span>
            </div>
            <div class="metric-value">{{ card.value }}</div>
            <div class="metric-bar">
              <span :style="{ width: `${card.ratio}%` }"></span>
            </div>
          </article>
        </div>

        <div class="hero-tags">
          <el-tag class="meta-tag" effect="dark" type="primary">
            任务 ID：{{ resolvedTaskId ?? '--' }}
          </el-tag>
          <el-tag class="meta-tag" effect="plain" type="info">
            条目 ID：{{ currentItem?.id ?? '--' }}
          </el-tag>
          <el-tag
            class="meta-tag"
            effect="plain"
            :type="currentItem?.dataType === 'CODE' ? 'warning' : 'success'"
          >
            类型：{{ currentItem?.dataType ?? '--' }}
          </el-tag>
        </div>
      </header>

      <transition name="fade-rise" mode="out-in">
        <el-empty
          v-if="!pageLoading && isFinished"
          key="finished"
          description="恭喜，当前任务已全部标注完毕！"
          class="finished-state glass-card"
        >
          <template #image>
            <div class="finished-core">
              <span>MISSION</span>
              <strong>DONE</strong>
            </div>
          </template>
          <el-button type="primary" @click="loadNextItem">重新检查</el-button>
        </el-empty>

        <div v-else key="workspace" class="workspace-grid">
          <section class="left-stage">
            <el-card class="glass-card stage-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <div>
                    <h3 class="card-title">清洗结果视图</h3>
                    <p class="card-desc">Monaco 风格的多模态代码/文本浏览区域</p>
                  </div>
                  <div class="status-pills">
                    <span class="status-pill live">
                      {{ pageLoading ? 'Loading stream' : 'Live item ready' }}
                    </span>
                  </div>
                </div>
              </template>

              <transition name="fade-rise" mode="out-in">
                <div v-if="pageLoading" key="left-skeleton" class="editor-skeleton">
                  <el-skeleton animated :rows="14">
                    <template #template>
                      <div class="skeleton-editor-shell">
                        <div class="skeleton-editor-toolbar"></div>
                        <div class="skeleton-editor-body">
                          <div class="skeleton-line-number" v-for="n in 12" :key="`ln-${n}`"></div>
                          <div class="skeleton-code-lines">
                            <el-skeleton-item
                              v-for="n in 12"
                              :key="`code-${n}`"
                              variant="text"
                              class="skeleton-code-line"
                            />
                          </div>
                        </div>
                      </div>
                    </template>
                  </el-skeleton>
                </div>

                <div v-else-if="currentItem" key="editor-view" class="editor-shell">
                  <div class="editor-toolbar">
                    <div class="editor-toolbar-left">
                      <span class="editor-dot red"></span>
                      <span class="editor-dot yellow"></span>
                      <span class="editor-dot green"></span>
                      <span class="editor-file">
                        {{ currentItem.dataType === 'CODE' ? 'cleaned_source.ts' : 'cleaned_content.txt' }}
                      </span>
                    </div>
                    <div class="editor-toolbar-right">
                      <span class="editor-badge">{{ currentItem.dataType }}</span>
                      <span class="editor-badge subtle">
                        {{ contentLineCount }} 行
                      </span>
                    </div>
                  </div>

                  <div class="editor-body">
                    <div class="editor-gutter">
                      <span
                        v-for="line in lineNumbers"
                        :key="`line-${line}`"
                        class="line-number"
                      >
                        {{ line }}
                      </span>
                    </div>

                    <div class="editor-content">
                      <template v-if="currentItem.dataType === 'CODE'">
                        <div
                          v-for="(line, index) in codeLines"
                          :key="`code-line-${index}`"
                          class="code-line"
                        >
                          <span class="fold-icon">{{ index % 7 === 0 ? '▸' : '' }}</span>
                          <span class="code-text" v-html="highlightCodeLine(line)"></span>
                        </div>
                      </template>

                      <template v-else>
                        <div
                          v-for="(line, index) in textLines"
                          :key="`text-line-${index}`"
                          class="code-line text-line"
                        >
                          <span class="fold-icon"></span>
                          <span class="code-text text-content">{{ line || ' ' }}</span>
                        </div>
                      </template>
                    </div>
                  </div>
                </div>

                <el-empty
                  v-else
                  key="left-empty"
                  description="请先输入任务 ID 并加载待标注条目"
                  class="empty-panel"
                />
              </transition>
            </el-card>
          </section>

          <section class="right-stage">
            <el-card class="glass-card stage-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <div>
                    <h3 class="card-title">AI 智能体看板</h3>
                    <p class="card-desc">推理链、结构化标签和专家微调都在这里完成</p>
                  </div>

                  <el-button
                    type="primary"
                    class="ai-adopt-button"
                    :disabled="!currentItem?.aiAnnotation"
                    :loading="aiAdoptAnimating"
                    @click="applyAiAnnotation"
                  >
                    采纳 AI 预标注
                  </el-button>
                </div>
              </template>

              <transition name="fade-rise" mode="out-in">
                <div v-if="pageLoading" key="right-skeleton" class="right-skeleton">
                  <el-skeleton animated>
                    <template #template>
                      <div class="skeleton-stack">
                        <div class="skeleton-card large"></div>
                        <div class="skeleton-card medium"></div>
                        <div class="skeleton-card form"></div>
                      </div>
                    </template>
                  </el-skeleton>
                </div>

                <div v-else-if="currentItem" key="right-content" class="right-stack">
                  <el-collapse class="thinking-chain" model-value="chain">
                    <el-collapse-item name="chain">
                      <template #title>
                        <div class="thinking-title">
                          <span>大模型推理思维链</span>
                          <small>Thinking Chain</small>
                        </div>
                      </template>

                      <ol class="chain-list">
                        <li v-for="(step, index) in thinkingChain" :key="`step-${index}`">
                          <span class="chain-index">0{{ index + 1 }}</span>
                          <div class="chain-body">
                            <strong>{{ step.title }}</strong>
                            <p>{{ step.content }}</p>
                          </div>
                        </li>
                      </ol>
                    </el-collapse-item>
                  </el-collapse>

                  <div class="annotation-insight">
                    <div class="insight-top">
                      <div class="insight-heading">
                        <span>结构化 AI 标签</span>
                        <small>Structured Annotation</small>
                      </div>

                      <div class="annotation-tags" v-if="currentItem.aiAnnotation">
                        <el-tag
                          size="large"
                          :type="currentItem.aiAnnotation.isBug ? 'danger' : 'success'"
                          effect="dark"
                        >
                          {{ currentItem.aiAnnotation.isBug ? 'Bug Detected' : 'Looks Safe' }}
                        </el-tag>
                        <el-tag
                          size="large"
                          effect="plain"
                          type="warning"
                        >
                          {{ currentItem.aiAnnotation.bugType || 'None' }}
                        </el-tag>
                      </div>
                    </div>

                    <div v-if="currentItem.aiAnnotation" class="json-grid">
                      <article class="json-chip bug">
                        <label>is_bug</label>
                        <strong>{{ String(currentItem.aiAnnotation.isBug) }}</strong>
                      </article>
                      <article class="json-chip type">
                        <label>bug_type</label>
                        <strong>{{ currentItem.aiAnnotation.bugType || 'None' }}</strong>
                      </article>
                      <article class="json-chip suggestion wide">
                        <label>suggestion</label>
                        <strong>{{ currentItem.aiAnnotation.suggestion || '暂无建议' }}</strong>
                      </article>
                    </div>

                    <el-empty
                      v-else
                      description="当前条目暂无 AI 预标注结果"
                      class="empty-panel compact"
                    />
                  </div>

                  <el-card
                    class="human-form-card"
                    shadow="never"
                    v-loading="submitLoading"
                  >
                    <template #header>
                      <div class="card-header form-header">
                        <div>
                          <h3 class="card-title">人工最终标注</h3>
                          <p class="card-desc">支持键盘流式操作，面向高强度标注场景</p>
                        </div>
                        <div class="mini-shortcut-tips">
                          <span><kbd>Alt + A</kbd> 采纳</span>
                          <span><kbd>Ctrl + Enter</kbd> 提交</span>
                        </div>
                      </div>
                    </template>

                    <el-form
                      ref="formRef"
                      :model="form"
                      :rules="formRules"
                      label-position="top"
                      class="annotation-form"
                    >
                      <transition name="fill-pop">
                        <div :key="formAnimationKey" class="form-inner">
                          <el-form-item label="是否为 Bug" prop="isBug">
                            <el-radio-group v-model="form.isBug" class="pill-radios">
                              <el-radio :value="true">是，存在缺陷</el-radio>
                              <el-radio :value="false">否，代码健壮</el-radio>
                            </el-radio-group>
                          </el-form-item>

                          <el-form-item label="错误类型" prop="bugType">
                            <el-input
                              v-model="form.bugType"
                              placeholder="例如：NullPointer / ResourceLeak / None"
                              clearable
                            />
                          </el-form-item>

                          <el-form-item label="修改建议" prop="suggestion">
                            <el-input
                              v-model="form.suggestion"
                              type="textarea"
                              :rows="6"
                              resize="vertical"
                              placeholder="请输入专家最终建议或修复方案"
                            />
                          </el-form-item>
                        </div>
                      </transition>

                      <div class="form-actions">
                        <el-button
                          class="ghost-button"
                          @click="resetFormByCurrentState"
                        >
                          重置
                        </el-button>
                        <el-button
                          type="primary"
                          class="submit-button"
                          :loading="submitLoading"
                          :disabled="!currentItem"
                          @click="submitAndNext"
                        >
                          提交并下一条
                        </el-button>
                      </div>
                    </el-form>
                  </el-card>
                </div>

                <el-empty
                  v-else
                  key="right-empty"
                  description="当前没有已加载的待标注条目"
                  class="empty-panel"
                />
              </transition>
            </el-card>
          </section>
        </div>
      </transition>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import {
  fetchNextAnnotationItem,
  submitExpertAnnotation,
} from '@/api/annotation'
import type {
  AiAnnotation,
  AnnotationFormModel,
  AnnotationItem,
  SubmitExpertAnnotationRequest,
} from '@/types/annotation'

const EXPERT_ID = 1

const route = useRoute()
const router = useRouter()

const pageLoading = ref(false)
const submitLoading = ref(false)
const isFinished = ref(false)
const currentItem = ref<AnnotationItem | null>(null)
const formRef = ref<FormInstance>()
const taskIdInput = ref('')
const formAnimationKey = ref(0)
const aiAdoptAnimating = ref(false)
const submitDebounceFlag = ref(false)

const resolvedTaskId = computed<number | null>(() => {
  const rawTaskId = route.params.taskId ?? route.query.taskId

  if (Array.isArray(rawTaskId)) {
    const parsed = Number(rawTaskId[0])
    return Number.isFinite(parsed) ? parsed : null
  }

  const parsed = Number(rawTaskId)
  return Number.isFinite(parsed) ? parsed : null
})

const form = reactive<AnnotationFormModel>({
  isBug: null,
  bugType: '',
  suggestion: '',
})

const formRules: FormRules<AnnotationFormModel> = {
  isBug: [
    {
      required: true,
      message: '请选择是否为 Bug',
      trigger: 'change',
    },
  ],
  bugType: [
    {
      required: true,
      message: '请输入错误类型',
      trigger: 'blur',
    },
  ],
  suggestion: [
    {
      required: true,
      message: '请输入修改建议',
      trigger: 'blur',
    },
  ],
}

const contentLineCount = computed(() => {
  const content = currentItem.value?.cleanedContent ?? ''
  if (!content) {
    return 0
  }
  return content.split('\n').length
})

const codeLines = computed(() => {
  const content = currentItem.value?.cleanedContent ?? ''
  return content ? content.split('\n') : []
})

const textLines = computed(() => {
  const content = currentItem.value?.cleanedContent ?? ''
  return content ? content.split('\n') : []
})

const lineNumbers = computed(() => {
  const count = Math.max(contentLineCount.value, 1)
  return Array.from({ length: count }, (_, index) => index + 1)
})

const metrics = computed(() => {
  const currentType = currentItem.value?.dataType ?? '--'
  const progressBase = currentItem.value ? 72 : 0

  return [
    {
      key: 'pending',
      label: '待处理队列',
      value: currentItem.value ? '01' : '--',
      trend: 'Awaiting Expert',
      ratio: currentItem.value ? 34 : 0,
    },
    {
      key: 'ai',
      label: 'AI 已推理',
      value: currentItem.value?.aiAnnotation ? '01' : '00',
      trend: currentItem.value?.aiAnnotation ? 'Inference Ready' : 'No Signal',
      ratio: currentItem.value?.aiAnnotation ? 78 : 0,
    },
    {
      key: 'done',
      label: '当前处理态',
      value: currentType,
      trend: currentItem.value ? 'Pipeline Live' : 'Idle',
      ratio: progressBase,
    },
  ]
})

const thinkingChain = computed(() => {
  if (!currentItem.value) {
    return []
  }

  const aiAnnotation = currentItem.value.aiAnnotation
  const dataType = currentItem.value.dataType

  return [
    {
      title: '输入归类',
      content:
        dataType === 'CODE'
          ? '系统识别为代码资产，优先执行结构风险与缺陷模式分析。'
          : '系统识别为文本资产，当前标注界面仍保留统一人工审阅链路。',
    },
    {
      title: '缺陷判断',
      content:
        aiAnnotation?.isBug == null
          ? 'AI 暂未形成确定性判断，等待专家补充最终结论。'
          : aiAnnotation.isBug
            ? '模型推断该内容存在明显缺陷，建议重点审查资源释放、空值路径与边界条件。'
            : '模型初判未发现显著缺陷，但仍建议专家关注业务约束与异常分支。',
    },
    {
      title: '类型归因',
      content: aiAnnotation?.bugType
        ? `模型已给出疑似缺陷类型：${aiAnnotation.bugType}。`
        : '模型尚未生成明确缺陷类型，建议人工补充专业判断。',
    },
    {
      title: '修复建议',
      content: aiAnnotation?.suggestion
        ? aiAnnotation.suggestion
        : '当前没有 AI 建议，专家可以直接写入最终处理意见。',
    },
  ]
})

async function applyTaskId() {
  const parsedTaskId = Number(taskIdInput.value)
  if (!Number.isFinite(parsedTaskId) || parsedTaskId <= 0) {
    ElMessage.warning('请输入有效的任务 ID')
    return
  }

  await router.push({
    name: 'annotation',
    params: { taskId: parsedTaskId },
  })
}

async function loadNextItem() {
  if (!resolvedTaskId.value) {
    currentItem.value = null
    isFinished.value = false
    return
  }

  pageLoading.value = true
  try {
    const nextItem = await fetchNextAnnotationItem(resolvedTaskId.value)
    currentItem.value = nextItem
    isFinished.value = !nextItem
    resetForm()

    if (!nextItem) {
      ElMessage.success('恭喜，当前任务已全部标注完毕！')
    }
  } catch (error) {
    console.error('loadNextItem error:', error)
    ElMessage.error('加载下一条待标注数据失败')
  } finally {
    pageLoading.value = false
  }
}

async function animateFormFill() {
  aiAdoptAnimating.value = true
  formAnimationKey.value += 1
  await nextTick()
  window.setTimeout(() => {
    aiAdoptAnimating.value = false
  }, 240)
}

async function applyAiAnnotation() {
  if (!currentItem.value?.aiAnnotation) {
    ElMessage.warning('当前没有可采纳的 AI 预标注结果')
    return
  }

  const aiResult: AiAnnotation = currentItem.value.aiAnnotation
  form.isBug = aiResult.isBug
  form.bugType = aiResult.bugType || ''
  form.suggestion = aiResult.suggestion || ''
  await animateFormFill()
  ElMessage.success('已采纳 AI 预标注结果')
}

function resetForm() {
  form.isBug = null
  form.bugType = ''
  form.suggestion = ''
  formRef.value?.clearValidate()
}

function resetFormByCurrentState() {
  resetForm()
  if (currentItem.value?.aiAnnotation) {
    ElMessage.info('表单已重置，可再次按 Alt + A 快速采纳 AI 结果')
  }
}

function buildExpertAnnotationPayload(): string {
  return JSON.stringify({
    is_bug: form.isBug,
    bug_type: form.bugType,
    suggestion: form.suggestion,
  })
}

async function submitAndNext() {
  if (!currentItem.value || submitLoading.value || submitDebounceFlag.value) {
    return
  }

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitDebounceFlag.value = true
  submitLoading.value = true

  try {
    const payload: SubmitExpertAnnotationRequest = {
      itemId: currentItem.value.id,
      expertId: EXPERT_ID,
      expertAnnotation: buildExpertAnnotationPayload(),
    }

    await submitExpertAnnotation(payload)
    ElMessage.success('标注提交成功，正在流式加载下一条...')
    currentItem.value = null
    resetForm()
    await loadNextItem()
  } catch (error) {
    console.error('submitAndNext error:', error)
    ElMessage.error('提交标注失败，请稍后重试')
  } finally {
    submitLoading.value = false
    window.setTimeout(() => {
      submitDebounceFlag.value = false
    }, 350)
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function highlightCodeLine(line: string): string {
  let escaped = escapeHtml(line)

  escaped = escaped.replace(
    /\b(import|from|export|return|if|else|for|while|class|public|private|protected|static|const|let|var|new|throw|try|catch|await|async|def|function)\b/g,
    '<span class="token keyword">$1</span>',
  )

  escaped = escaped.replace(
    /\b(true|false|null|undefined)\b/g,
    '<span class="token literal">$1</span>',
  )

  escaped = escaped.replace(
    /("[^"]*"|'[^']*')/g,
    '<span class="token string">$1</span>',
  )

  escaped = escaped.replace(
    /\b(\d+)\b/g,
    '<span class="token number">$1</span>',
  )

  escaped = escaped.replace(
    /(\/\/.*$|#.*$)/g,
    '<span class="token comment">$1</span>',
  )

  return escaped || '&nbsp;'
}

function handleGlobalShortcut(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null
  const isTyping =
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target?.isContentEditable === true

  if (event.altKey && (event.key === 'a' || event.key === 'A')) {
    event.preventDefault()
    if (!pageLoading.value && !isTyping) {
      applyAiAnnotation()
    }
    return
  }

  if (event.ctrlKey && event.key === 'Enter') {
    event.preventDefault()
    submitAndNext()
  }
}

onMounted(() => {
  if (resolvedTaskId.value) {
    taskIdInput.value = String(resolvedTaskId.value)
  }
  loadNextItem()
  window.addEventListener('keydown', handleGlobalShortcut)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalShortcut)
})

watch(
  resolvedTaskId,
  (value) => {
    if (value) {
      taskIdInput.value = String(value)
      loadNextItem()
    }
  },
)
</script>

<style scoped>
.annotation-shell {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 20% 20%, rgba(0, 255, 214, 0.1), transparent 22%),
    radial-gradient(circle at 80% 10%, rgba(90, 92, 255, 0.16), transparent 26%),
    radial-gradient(circle at 70% 80%, rgba(255, 90, 138, 0.12), transparent 22%),
    linear-gradient(180deg, #050816 0%, #07111f 38%, #03050d 100%);
}

.annotation-page {
  position: relative;
  z-index: 2;
  min-height: 100vh;
  padding: 28px;
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(100px);
  opacity: 0.55;
  pointer-events: none;
}

.ambient-left {
  top: 6%;
  left: -6%;
  width: 320px;
  height: 320px;
  background: rgba(11, 233, 198, 0.16);
}

.ambient-right {
  top: 18%;
  right: -8%;
  width: 380px;
  height: 380px;
  background: rgba(101, 110, 255, 0.18);
}

.ambient-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(68, 95, 138, 0.14) 1px, transparent 1px),
    linear-gradient(90deg, rgba(68, 95, 138, 0.14) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent 85%);
  pointer-events: none;
}

.glass-card {
  border: 1px solid rgba(130, 164, 255, 0.18);
  background: linear-gradient(180deg, rgba(11, 18, 34, 0.88), rgba(9, 14, 28, 0.8));
  box-shadow:
    0 10px 30px rgba(0, 0, 0, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(18px);
}

.hero-panel {
  padding: 22px 24px 24px;
  border-radius: 28px;
}

.hero-main {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}

.eyebrow {
  margin: 0 0 10px;
  color: #47f3d1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.hero-title {
  margin: 0;
  color: #f8fbff;
  font-size: 34px;
  font-weight: 800;
  line-height: 1.08;
}

.hero-subtitle {
  margin: 14px 0 0;
  max-width: 760px;
  color: #8ea0bf;
  font-size: 15px;
  line-height: 1.8;
}

.hero-controls {
  display: grid;
  gap: 12px;
  justify-items: end;
  min-width: 320px;
}

.task-input {
  width: 320px;
}

:deep(.task-input .el-input__wrapper),
:deep(.task-input .el-input-group__prepend),
:deep(.task-input .el-input-group__append) {
  background: rgba(13, 22, 42, 0.92);
  border-color: rgba(94, 132, 255, 0.18);
  color: #eaf1ff;
}

.task-label {
  color: #85f0db;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.shortcut-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #91a6c7;
  font-size: 12px;
}

kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  padding: 4px 8px;
  border: 1px solid rgba(87, 230, 199, 0.18);
  border-radius: 10px;
  background: rgba(11, 28, 33, 0.75);
  color: #91ffea;
  font-size: 11px;
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 24px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid rgba(123, 150, 214, 0.14);
  background: linear-gradient(180deg, rgba(14, 23, 42, 0.95), rgba(12, 19, 36, 0.82));
}

.metric-card::after {
  content: '';
  position: absolute;
  inset: auto -10% -55% auto;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  opacity: 0.22;
  filter: blur(20px);
}

.metric-pending::after {
  background: rgba(255, 186, 74, 0.7);
}

.metric-ai::after {
  background: rgba(56, 255, 217, 0.65);
}

.metric-done::after {
  background: rgba(113, 140, 255, 0.7);
}

.metric-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #85f0db;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-label {
  color: #8ca1c2;
}

.metric-trend {
  color: #85f0db;
}

.metric-value {
  margin-top: 14px;
  color: #f3f7ff;
  font-size: 34px;
  font-weight: 800;
  line-height: 1;
}

.metric-bar {
  height: 6px;
  margin-top: 16px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
}

.metric-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #44d4ff, #54f4c4);
  box-shadow: 0 0 18px rgba(84, 244, 196, 0.55);
  animation: pulse-bar 2.4s ease-in-out infinite;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.meta-tag {
  border-radius: 999px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.22fr) minmax(400px, 0.78fr);
  gap: 22px;
  margin-top: 22px;
  align-items: start;
}

.stage-card {
  border-radius: 28px;
}

.left-stage,
.right-stage {
  min-width: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.card-title {
  margin: 0;
  color: #f7fbff;
  font-size: 18px;
  font-weight: 700;
}

.card-desc {
  margin: 6px 0 0;
  color: #7f94b5;
  font-size: 13px;
}

.status-pills {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-pill {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.status-pill.live {
  color: #89ffea;
  background: rgba(23, 55, 58, 0.78);
  border: 1px solid rgba(68, 231, 196, 0.22);
}

.editor-shell {
  overflow: hidden;
  border: 1px solid rgba(80, 104, 164, 0.18);
  border-radius: 22px;
  background: #0a1020;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(110, 134, 186, 0.14);
  background: linear-gradient(180deg, rgba(18, 27, 49, 0.92), rgba(14, 22, 40, 0.82));
}

.editor-toolbar-left,
.editor-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.editor-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.editor-dot.red {
  background: #ff6c87;
}

.editor-dot.yellow {
  background: #ffca57;
}

.editor-dot.green {
  background: #4de1a8;
}

.editor-file {
  color: #c8d7f7;
  font-size: 13px;
}

.editor-badge {
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(86, 110, 255, 0.12);
  color: #a7b6ff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.editor-badge.subtle {
  background: rgba(255, 255, 255, 0.05);
  color: #88a0c5;
}

.editor-body {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  min-height: 700px;
  max-height: 700px;
  overflow: auto;
}

.editor-body::-webkit-scrollbar,
.code-block::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

.editor-body::-webkit-scrollbar-thumb,
.code-block::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(110, 134, 186, 0.35);
}

.editor-body::-webkit-scrollbar-track,
.code-block::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.04);
}

.editor-gutter {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding: 18px 10px 18px 0;
  gap: 8px;
  border-right: 1px solid rgba(110, 134, 186, 0.12);
  background: linear-gradient(180deg, rgba(6, 10, 22, 0.95), rgba(8, 13, 24, 0.92));
}

.line-number {
  color: #4c6287;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  line-height: 1.9;
}

.editor-content {
  padding: 18px 0;
}

.code-line {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  min-height: 26px;
  padding: 0 20px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.9;
  color: #e5eeff;
}

.code-line:hover {
  background: rgba(88, 111, 255, 0.08);
}

.fold-icon {
  color: #4f668f;
  user-select: none;
}

.code-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.text-content {
  color: #dce8ff;
}

:deep(.token.keyword) {
  color: #8f9cff;
}

:deep(.token.literal) {
  color: #57efc8;
}

:deep(.token.string) {
  color: #ffc86a;
}

:deep(.token.number) {
  color: #ff8db4;
}

:deep(.token.comment) {
  color: #6981a6;
  font-style: italic;
}

.thinking-chain {
  border: 1px solid rgba(113, 139, 200, 0.14);
  border-radius: 22px;
  overflow: hidden;
  background: rgba(8, 15, 31, 0.72);
}

:deep(.thinking-chain .el-collapse-item__header) {
  background: transparent;
  color: #ebf2ff;
  border-bottom-color: rgba(113, 139, 200, 0.12);
  padding: 0 18px;
  min-height: 58px;
}

:deep(.thinking-chain .el-collapse-item__wrap) {
  background: transparent;
  border-bottom: none;
}

:deep(.thinking-chain .el-collapse-item__content) {
  color: inherit;
  padding: 0 18px 18px;
}

.thinking-title {
  display: flex;
  flex-direction: column;
}

.thinking-title span {
  color: #f5f9ff;
  font-weight: 700;
}

.thinking-title small {
  color: #6fb9ff;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.chain-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chain-list li {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.chain-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(35, 89, 255, 0.22), rgba(0, 229, 181, 0.14));
  color: #82ffe7;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.chain-body {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(13, 21, 39, 0.88);
  border: 1px solid rgba(113, 139, 200, 0.12);
}

.chain-body strong {
  display: block;
  color: #eff5ff;
  font-size: 14px;
}

.chain-body p {
  margin: 6px 0 0;
  color: #8fa5c7;
  font-size: 13px;
  line-height: 1.7;
}

.right-stack {
  display: grid;
  gap: 18px;
}

.annotation-insight {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(103, 128, 188, 0.14);
  background: linear-gradient(180deg, rgba(8, 16, 32, 0.92), rgba(8, 14, 26, 0.82));
}

.insight-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.insight-heading span {
  display: block;
  color: #f7fbff;
  font-size: 16px;
  font-weight: 700;
}

.insight-heading small {
  color: #6fb9ff;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.annotation-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.json-chip {
  display: grid;
  gap: 8px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(122, 146, 204, 0.12);
  background: rgba(12, 20, 38, 0.85);
}

.json-chip.wide {
  grid-column: 1 / -1;
}

.json-chip label {
  color: #6f87ab;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.json-chip strong {
  color: #edf5ff;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
}

.json-chip.bug {
  box-shadow: inset 0 0 0 1px rgba(255, 111, 145, 0.06);
}

.json-chip.type {
  box-shadow: inset 0 0 0 1px rgba(255, 201, 103, 0.05);
}

.json-chip.suggestion {
  box-shadow: inset 0 0 0 1px rgba(109, 246, 221, 0.05);
}

.human-form-card {
  border: 1px solid rgba(122, 146, 204, 0.12);
  border-radius: 22px;
  background: rgba(8, 15, 29, 0.76);
}

:deep(.human-form-card .el-card__header) {
  border-bottom-color: rgba(122, 146, 204, 0.12);
}

.form-header {
  align-items: center;
}

.mini-shortcut-tips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  color: #88a0c5;
  font-size: 12px;
}

.annotation-form {
  margin-top: 2px;
}

.form-inner {
  display: grid;
  gap: 4px;
}

:deep(.annotation-form .el-form-item__label) {
  color: #d8e7ff;
  font-weight: 600;
}

:deep(.annotation-form .el-input__wrapper),
:deep(.annotation-form .el-textarea__inner) {
  background: rgba(10, 18, 35, 0.9);
  color: #e7f0ff;
  border-color: rgba(115, 142, 205, 0.14);
  box-shadow: inset 0 0 0 1px rgba(115, 142, 205, 0.08);
}

:deep(.annotation-form .el-textarea__inner) {
  min-height: 160px;
}

.pill-radios {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

:deep(.pill-radios .el-radio) {
  margin-right: 0;
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(13, 21, 39, 0.88);
  border: 1px solid rgba(115, 142, 205, 0.12);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.ghost-button {
  border-color: rgba(115, 142, 205, 0.16);
  background: rgba(11, 20, 37, 0.92);
  color: #d8e7ff;
}

.submit-button {
  min-width: 180px;
  background: linear-gradient(90deg, #4968ff, #35d7ff);
  border: none;
  box-shadow: 0 10px 30px rgba(53, 215, 255, 0.25);
}

.finished-state {
  margin-top: 28px;
  padding: 40px 24px;
  border-radius: 28px;
}

.finished-core {
  display: grid;
  place-items: center;
  width: 132px;
  height: 132px;
  margin: 0 auto;
  border-radius: 32px;
  background: linear-gradient(135deg, #18b36f 0%, #08b8cc 100%);
  color: #fff;
  box-shadow: 0 20px 60px rgba(8, 184, 204, 0.28);
}

.finished-core span {
  font-size: 12px;
  letter-spacing: 0.16em;
}

.finished-core strong {
  font-size: 28px;
  letter-spacing: 0.08em;
}

.empty-panel {
  min-height: 420px;
}

.empty-panel.compact {
  min-height: 180px;
}

.editor-skeleton,
.right-skeleton {
  min-height: 520px;
}

.skeleton-editor-shell {
  padding: 0;
  overflow: hidden;
  border-radius: 22px;
  background: rgba(9, 15, 28, 0.86);
}

.skeleton-editor-toolbar {
  height: 48px;
  border-bottom: 1px solid rgba(110, 134, 186, 0.08);
  background: rgba(14, 22, 40, 0.85);
}

.skeleton-editor-body {
  display: grid;
  grid-template-columns: 70px 1fr;
  min-height: 520px;
  padding: 18px 0;
}

.skeleton-line-number {
  height: 20px;
  margin: 8px 16px 8px auto;
  width: 26px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
}

.skeleton-code-lines {
  padding-right: 20px;
}

.skeleton-code-line {
  display: block;
  height: 18px;
  margin: 10px 0;
  border-radius: 8px;
}

.skeleton-stack {
  display: grid;
  gap: 16px;
}

.skeleton-card {
  border-radius: 22px;
  background: rgba(12, 19, 35, 0.85);
}

.skeleton-card.large {
  min-height: 210px;
}

.skeleton-card.medium {
  min-height: 200px;
}

.skeleton-card.form {
  min-height: 320px;
}

.fade-rise-enter-active,
.fade-rise-leave-active {
  transition: all 0.28s ease;
}

.fade-rise-enter-from,
.fade-rise-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

.fill-pop-enter-active {
  animation: fill-pop 0.28s ease;
}

@keyframes pulse-bar {
  0%,
  100% {
    opacity: 0.88;
    transform: scaleX(0.98);
  }
  50% {
    opacity: 1;
    transform: scaleX(1);
  }
}

@keyframes fill-pop {
  0% {
    opacity: 0.55;
    transform: translateY(10px) scale(0.99);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 1320px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .hero-main {
    flex-direction: column;
  }

  .hero-controls {
    justify-items: start;
    min-width: 0;
  }
}

@media (max-width: 820px) {
  .annotation-page {
    padding: 16px;
  }

  .hero-title {
    font-size: 28px;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .task-input {
    width: 100%;
  }

  .editor-body {
    max-height: 520px;
  }

  .insight-top {
    flex-direction: column;
  }

  .json-grid {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column;
  }

  .submit-button,
  .ghost-button {
    width: 100%;
  }
}
</style>
