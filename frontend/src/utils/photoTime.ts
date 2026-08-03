import * as exifr from 'exifr'

const filenameTimePatterns = [
  /(?:^|\D)(\d{4})(\d{2})(\d{2})[_-]?(\d{2})(\d{2})(\d{2})(?:\D|$)/,
  /(?:^|\D)(\d{4})[-_.](\d{2})[-_.](\d{2})[ T_-]?(\d{2})[-_.:]?(\d{2})(?:[-_.:]?(\d{2}))?(?:\D|$)/
]
const wechatCameraTime = /^wx_camera_(\d{13})(?:\D|$)/i

function validTime(parts: string[]): string {
  const [year, month, day, hour, minute] = parts.map(Number)
  const value = new Date(year, month - 1, day, hour, minute)
  if (value.getFullYear() !== year || value.getMonth() !== month - 1 ||
      value.getDate() !== day || value.getHours() !== hour || value.getMinutes() !== minute) return ''
  return `${parts[0]}-${parts[1]}-${parts[2]} ${parts[3]}:${parts[4]}`
}

export function photoTimeFromFilename(filename: string): string {
  for (const pattern of filenameTimePatterns) {
    const match = filename.match(pattern)
    if (match) {
      const value = validTime(match.slice(1, 6))
      if (value) return value
    }
  }
  const wechatMatch = filename.match(wechatCameraTime)
  if (wechatMatch) {
    const value = new Date(Number(wechatMatch[1]))
    const year = value.getFullYear()
    if (!Number.isNaN(value.getTime()) && year >= 2000 && year <= new Date().getFullYear() + 1) {
      return value.toLocaleString('sv-SE').slice(0, 16)
    }
  }
  return ''
}

export async function detectPhotoTime(file: File): Promise<string> {
  try {
    const data: any = await exifr.parse(file, ['DateTimeOriginal', 'CreateDate', 'ModifyDate'])
    const value = data?.DateTimeOriginal || data?.CreateDate || data?.ModifyDate
    if (value instanceof Date && !Number.isNaN(value.getTime())) {
      return value.toLocaleString('sv-SE').slice(0, 16)
    }
  } catch {
    // Images saved by social apps commonly contain no readable capture-time EXIF.
  }
  return photoTimeFromFilename(file.name)
}
