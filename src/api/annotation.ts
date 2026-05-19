import axios, { type AxiosInstance } from 'axios'
import type {
  AnnotationItem,
  ApiResult,
  SubmitExpertAnnotationRequest,
} from '@/types/annotation'

/**
 * 独立的标注模块 Axios 实例。
 *
 * 说明：
 * 1. baseURL 可以通过 Vite 环境变量覆盖；
 * 2. 如果你的项目已有统一 request 实例，可以把这里替换成已有封装。
 */
const annotationHttp: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API ?? '/dev-api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 统一处理后端 Result<T> 包装。
 * 如果 code !== 0，直接抛错给上层视图处理。
 */
function unwrapResult<T>(result: ApiResult<T>): T {
  if (result.code !== 0) {
    throw new Error(result.message || '接口请求失败')
  }
  return result.data
}

/**
 * 获取当前任务的下一条待专家标注数据。
 *
 * @param taskId 当前任务 ID
 * @returns 如果后端返回 null，表示该任务已全部标注完成
 */
export async function fetchNextAnnotationItem(
  taskId: number,
): Promise<AnnotationItem | null> {
  const { data } = await annotationHttp.get<ApiResult<AnnotationItem | null>>(
    '/api/v1/annotation/next',
    {
      params: { taskId },
    },
  )

  return unwrapResult(data)
}

/**
 * 提交专家最终标注结果。
 *
 * @param payload 提交请求体
 */
export async function submitExpertAnnotation(
  payload: SubmitExpertAnnotationRequest,
): Promise<void> {
  const { data } = await annotationHttp.post<ApiResult<null>>(
    '/api/v1/annotation/submit',
    payload,
  )

  unwrapResult(data)
}

export { annotationHttp }
