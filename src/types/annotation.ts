/**
 * 后端统一响应结构。
 * 与 Java 侧 Result<T> 对齐。
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/**
 * AI 预标注结果结构。
 * 对齐后端 ai_annotation / expert_annotation 的 JSON 对象。
 */
export interface AiAnnotation {
  isBug: boolean | null
  bugType: string
  suggestion: string
}

/**
 * 待标注条目结构。
 * 对齐 GET /api/v1/annotation/next 返回的 data 部分。
 */
export interface AnnotationItem {
  id: number
  dataType: 'TEXT' | 'CODE'
  cleanedContent: string
  aiAnnotation: AiAnnotation | null
}

/**
 * 提交专家最终标注的请求体。
 * 对齐 POST /api/v1/annotation/submit 的 DTO 结构。
 */
export interface SubmitExpertAnnotationRequest {
  itemId: number
  expertId: number
  expertAnnotation: string
}

/**
 * 人工标注表单结构。
 * 视图层使用的内部模型，最终会被序列化成 expertAnnotation JSON 字符串。
 */
export interface AnnotationFormModel {
  isBug: boolean | null
  bugType: string
  suggestion: string
}
