export const formatPrice = (price: number) => (price === 0 ? '免费' : `¥${price}`)
export const percent = (value: number, total: number) => Math.min(100, Math.round((value / total) * 100))
export const cn = (...names: Array<string | false | undefined>) => names.filter(Boolean).join(' ')
