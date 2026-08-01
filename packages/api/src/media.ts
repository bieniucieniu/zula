import { customInstance } from "./mutator"

export type UploadMediaResponse = {
  objectKey: string
  publicUrl: string
}

/** POST file bytes through Ktor → MinIO; returns object key for feature writes. */
export async function uploadImageObject(file: File): Promise<{
  objectKey: string
  publicUrl: string
}> {
  const { data } = await customInstance<{ data: UploadMediaResponse; status: number }>(
    "/api/media/uploads",
    {
      method: "POST",
      headers: {
        "Content-Type": file.type || "image/jpeg",
      },
      body: file,
    }
  )
  return { objectKey: data.objectKey, publicUrl: data.publicUrl }
}
