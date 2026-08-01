import { customInstance } from "./mutator"

export type RequestUploadRequest = {
  contentType: string
  contentLength?: number | null
}

export type RequestUploadResponse = {
  objectKey: string
  uploadUrl: string
  headers: Record<string, string>
  publicUrl: string
  expiresAt: string
}

export async function requestUpload(
  body: RequestUploadRequest
): Promise<{ data: RequestUploadResponse; status: number }> {
  return customInstance<{ data: RequestUploadResponse; status: number }>("/api/media/uploads", {
    method: "POST",
    body: JSON.stringify(body),
  })
}

/** Presign → PUT bytes to MinIO → returns object key for feature writes. */
export async function uploadImageObject(file: File): Promise<{
  objectKey: string
  publicUrl: string
}> {
  const { data } = await requestUpload({
    contentType: file.type || "image/jpeg",
    contentLength: file.size,
  })
  const headers = new Headers()
  for (const [key, value] of Object.entries(data.headers ?? {})) {
    headers.set(key, value)
  }
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", file.type || "image/jpeg")
  }
  const put = await fetch(data.uploadUrl, {
    method: "PUT",
    headers,
    body: file,
  })
  if (!put.ok) {
    throw new Error(`Upload failed (${put.status})`)
  }
  return { objectKey: data.objectKey, publicUrl: data.publicUrl }
}
