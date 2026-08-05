export const isVideoFile = (file: File) => file.type.startsWith('video/') || /\.(mp4|mov|m4v|webm|3gp)$/i.test(file.name)

export function formatDuration(milliseconds?: number) {
  const total = Math.max(0, Math.round((milliseconds || 0) / 1000))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor(total % 3600 / 60)
  const seconds = total % 60
  return hours
    ? `${hours}:${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')}`
    : `${minutes}:${String(seconds).padStart(2,'0')}`
}
