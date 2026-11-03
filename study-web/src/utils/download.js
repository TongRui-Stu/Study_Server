export function downloadBlobResponse(response, fallbackFilename) {
  const disposition = response.headers?.['content-disposition'] || ''
  const filename = parseFilename(disposition) || fallbackFilename
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function getDownloadErrorMessage(error, fallbackMessage) {
  const data = error.response?.data
  if (!(data instanceof Blob)) {
    return data?.message || fallbackMessage
  }
  try {
    const body = JSON.parse(await data.text())
    return body.message || fallbackMessage
  } catch {
    return fallbackMessage
  }
}

function parseFilename(disposition) {
  const encodedMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encodedMatch) {
    try {
      return decodeURIComponent(encodedMatch[1].trim())
    } catch {
      return encodedMatch[1].trim()
    }
  }
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1]?.trim() || ''
}
