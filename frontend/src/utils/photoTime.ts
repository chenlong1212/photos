import * as exifr from 'exifr'

const filenameTimePatterns = [
  /(?:^|\D)(\d{4})(\d{2})(\d{2})[_-]?(\d{2})(\d{2})(\d{2})(?:\D|$)/,
  /(?:^|\D)(\d{4})[-_.](\d{2})[-_.](\d{2})[ T_-]?(\d{2})[-_.:]?(\d{2})(?:[-_.:]?(\d{2}))?(?:\D|$)/
]

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
