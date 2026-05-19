<template>
  <div class="annotation-page" v-loading="pageLoading">
    <div class="top-bar">
      <div class="title-group">
        <h2 class="page-title">数据标注工作台</h2>
        <p class="page-subtitle">专家对 AI 预标注结果进行确认、微调并流水线提交</p>
      </div>

      <el-space wrap>
        <el-tag effect="dark" type="primary">任务 ID：{{ taskId }}</el-tag>
        <el-tag effect="plain" type="info">
          条目 ID：{{ currentItem?.id ?? '--' }}
        </el-tag>
        <el-tag effect="plain" :type="currentItem?.dataType === 'CODE' ? 'warning' : 'success'">
          类型：{{ currentItem?.dataType ?? '--' }}
        </el-tag>
      </el-space>
    </div>

    <el-empty
      v-if="!pageLoading && isFinished"
      description="恭喜，当前任务已全部标注完毕！"
      class="finished-state"
    >
      <template #image>
        <div class="finished-badge">DONE</div>
      </template>
      <el-button type="primary" @click="loadNextItem">重新检查</el-button>
    </el-empty>

    <div v-else class="content-grid">
      <el-card class="panel-card content-card" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>清洗后内容</span>
            <el-tag v-if="currentItem" size="small" effect="plain">
              {{ currentItem.dataType === 'CODE' ? '代码视图' : '文本视图' }}
            </el-tag>
          </div>
        </template>

        <div v-if="currentItem" class="content-view">
          <pre
            v-if="currentItem.dataType === 'CODE'"
            class="code-block"
          ><code>{{ currentItem.cleanedContent || '暂无内容' }}</code></pre>

          <div v-else class="text-block">
            {{ currentItem.cleanedContent || '暂无内容' }}
          </div>
        </div>

        <el-empty
          v-else
          description="当前没有可展示的数据内容"
        />
      </el-card>

      <div class="right-panel">
        <el-card class="panel-card ai-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>AI 预标注结果</span>
              <el-button
                type="primary"
                plain
                :disabled="!currentItem?.aiAnnotation"
                @click="applyAiAnnotation"
              >
                采纳 AI 预标注
              </el-button>
            </div>
          </template>

          <template v-if="currentItem?.aiAnnotation">
            <el-descriptions :column="1" border size="small" class="ai-descriptions">
              <el-descriptions-item label="是否为 Bug">
                <el-tag :type="currentItem.aiAnnotation.isBug ? 'danger' : 'success'">
                  {{ currentItem.aiAnnotation.isBug ? '是' : '否' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="错误类型">
                {{ currentItem.aiAnnotation.bugType || 'None' }}
              </el-descriptions-item>
              <el-descriptions-item label="修复建议">
                {{ currentItem.aiAnnotation.suggestion || '暂无建议' }}
              </el-descriptions-item>
            </el-descriptions>
          </template>

          <el-empty
            v-else
            description="当前条目暂无 AI 预标注结果"
          />
        </el-card>

        <el-card class="panel-card form-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>人工最终标注</span>
            </div>
          </template>

          <el-form
            ref="formRef"
            :model="form"
            :rules="formRules"
            label-position="top"
            class="annotation-form"
          >
            <el-form-item label="是否为 Bug" prop="isBug">
              <el-radio-group v-model="form.isBug">
                <el-radio :value="true">是</el-radio>
                <el-radio :value="false">否</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="错误类型" prop="bugType">
              <el-input
                v-model="form.bugType"
                placeholder="例如：NullPointer / SQLSyntax / None"
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

            <div class="form-actions">
              <el-button @click="resetFormByCurrentState">
                重置
              </el-button>
              <el-button
                type="primary"
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import axios from 'axios'

/**
 * AI 预标注结构。
 * 对齐后端返回的 ai_annotation JSON 对象结构。
 */
interface AiAnnotation {
  isBug: boolean | null
  bugType: string
  suggestion: string
}

/**
 * 获取下一条待标注数据接口的响应体 data 结构。
 */
interface AnnotationItem {
  id: number
  dataType: 'TEXT' | 'CODE'
  cleanedContent: string
  aiAnnotation: AiAnnotation | null
}

/**
 * 后端统一 Result 包装结构。
 */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/**
 * 提交专家标注请求体。
 * expertId 这里先按需求写死为 1，后续可以改为从 Pinia / 用户态读取。
 */
interface SubmitExpertAnnotationPayload {
  itemId: number
  expertId: number
  expertAnnotation: string
}

/**
 * 组件入参：由父页面传入当前任务 ID。
 * 也可以很容易改造成从路由 query 或 params 读取。
 */
const props = defineProps<{
  taskId: number
}>()

/**
 * Axios 实例。
 * 这里统一约定 baseURL 指向你的 Java data-service。
 * 如果前端项目有全局请求封装，可以把这里替换成现有 http 客户端。
 */
const request = axios.create({
  baseURL: '/',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const pageLoading = ref(false)
const submitLoading = ref(false)
const isFinished = ref(false)
const currentItem = ref<AnnotationItem | null>(null)
const formRef = ref<FormInstance>()

/**
 * 人工标注表单。
 * 与后端 expert_annotation 最终写入结构保持一致。
 */
const form = reactive<AiAnnotation>({
  isBug: null,
  bugType: '',
  suggestion: '',
})

/**
 * 表单校验规则。
 */
const formRules: FormRules<typeof form> = {
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

const hasAiAnnotation = computed(() => Boolean(currentItem.value?.aiAnnotation))

/**
 * 加载当前任务的下一条待标注数据。
 * - 如果后端返回 null，表示当前任务已标注完成；
 * - 这时展示空状态并提示用户。
 */
async function loadNextItem() {
  pageLoading.value = true
  try {
    const { data } = await request.get<ApiResult<AnnotationItem | null>>(
      '/api/v1/annotation/next',
      {
        params: {
          taskId: props.taskId,
        },
      },
    )

    if (data.code !== 0) {
      throw new Error(data.message || '加载下一条标注数据失败')
    }

    currentItem.value = data.data
    isFinished.value = !data.data

    if (data.data) {
      resetForm()
    } else {
      resetForm()
      ElMessage.success('恭喜，当前任务已全部标注完毕！')
    }
  } catch (error) {
    console.error('loadNextItem error:', error)
    ElMessage.error('加载下一条待标注数据失败')
  } finally {
    pageLoading.value = false
  }
}

/**
 * 将 AI 预标注结果一键复制到人工表单中。
 * 这样专家只需要做小幅修正即可。
 */
function applyAiAnnotation() {
  if (!currentItem.value?.aiAnnotation) {
    ElMessage.warning('当前没有可采纳的 AI 预标注结果')
    return
  }

  form.isBug = currentItem.value.aiAnnotation.isBug
  form.bugType = currentItem.value.aiAnnotation.bugType || ''
  form.suggestion = currentItem.value.aiAnnotation.suggestion || ''

  ElMessage.success('已采纳 AI 预标注结果')
}

/**
 * 根据当前条目状态重置人工表单。
 * 默认清空，避免上一条数据残留污染下一条。
 */
function resetForm() {
  form.isBug = null
  form.bugType = ''
  form.suggestion = ''
  formRef.value?.clearValidate()
}

/**
 * 用户手动点击“重置”时，如果当前存在 AI 结果，则提示可以再一键采纳。
 */
function resetFormByCurrentState() {
  resetForm()
  if (hasAiAnnotation.value) {
    ElMessage.info('表单已重置，你也可以点击“采纳 AI 预标注”快速填充')
  }
}

/**
 * 将人工表单内容序列化成后端需要的 JSON 字符串。
 * 后端 expert_annotation 支持 JSON 或纯文本，这里优先按结构化 JSON 提交。
 */
function buildExpertAnnotationPayload(): string {
  return JSON.stringify({
    is_bug: form.isBug,
    bug_type: form.bugType,
    suggestion: form.suggestion,
  })
}

/**
 * 提交当前标注，并自动加载下一条数据，实现流水线式标注体验。
 * 这里显式加 submitLoading，防止专家连续点击造成重复提交。
 */
async function submitAndNext() {
  if (!currentItem.value) {
    ElMessage.warning('当前没有可提交的标注数据')
    return
  }

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitLoading.value = true
  try {
    const payload: SubmitExpertAnnotationPayload = {
      itemId: currentItem.value.id,
      expertId: 1,
      expertAnnotation: buildExpertAnnotationPayload(),
    }

    const { data } = await request.post<ApiResult<null>>(
      '/api/v1/annotation/submit',
      payload,
    )

    if (data.code !== 0) {
      throw new Error(data.message || '提交标注失败')
    }

    ElMessage.success('标注提交成功，正在加载下一条...')

    /**
     * 提交成功后立即清空界面状态，避免用户误以为当前条目还未提交。
     */
    currentItem.value = null
    resetForm()

    await loadNextItem()
  } catch (error) {
    console.error('submitAndNext error:', error)
    ElMessage.error('提交标注失败，请稍后重试')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadNextItem()
})
</script>

<style scoped>
.annotation-page {
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(48, 92, 222, 0.08), transparent 28%),
    radial-gradient(circle at bottom right, rgba(22, 163, 74, 0.08), transparent 24%),
    linear-gradient(180deg, #f7f9fc 0%, #eef3f8 100%);
}

.top-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.title-group {
  min-width: 0;
}

.page-title {
  margin: 0;
  color: #1f2937;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.page-subtitle {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 20px;
  align-items: start;
}

.right-panel {
  display: grid;
  gap: 20px;
}

.panel-card {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
  color: #111827;
}

.content-view {
  min-height: 560px;
}

.code-block {
  margin: 0;
  padding: 20px;
  min-height: 560px;
  overflow: auto;
  border-radius: 16px;
  background: linear-gradient(180deg, #0f172a 0%, #111827 100%);
  color: #e5eefb;
  font-size: 13px;
  line-height: 1.7;
  font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.05);
}

.text-block {
  min-height: 560px;
  padding: 20px;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  color: #1f2937;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.15);
}

.ai-descriptions {
  overflow: hidden;
}

.annotation-form {
  margin-top: 4px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.finished-state {
  margin-top: 80px;
}

.finished-badge {
  display: grid;
  place-items: center;
  width: 112px;
  height: 112px;
  margin: 0 auto;
  border-radius: 28px;
  background: linear-gradient(135deg, #16a34a 0%, #0f766e 100%);
  color: #fff;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0.08em;
  box-shadow: 0 18px 40px rgba(15, 118, 110, 0.24);
}

@media (max-width: 1080px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .top-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .annotation-page {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .code-block,
  .text-block,
  .content-view {
    min-height: 360px;
  }

  .form-actions {
    flex-direction: column;
  }

  .form-actions .el-button {
    width: 100%;
  }
}
</style>
