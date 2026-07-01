export type SupportedCity = '杭州' | '北京'

export interface CityConfig {
  name: SupportedCity
  center: [number, number]
  zoom: number
  districts: string[]
  recommendedPlace: { location: string; district: string; longitude: number; latitude: number }
}

export const cityConfigs: Record<SupportedCity, CityConfig> = {
  杭州: {
    name: '杭州',
    center: [30.274085, 120.15507],
    zoom: 12,
    districts: ['拱墅区', '西湖区', '上城区', '滨江区', '余杭区', '萧山区', '钱塘区', '临平区'],
    recommendedPlace: { location: '桥西历史文化街区游客中心', district: '拱墅区', longitude: 120.139863, latitude: 30.318332 },
  },
  北京: {
    name: '北京',
    center: [39.9042, 116.4074],
    zoom: 11,
    districts: ['东城区', '西城区', '朝阳区', '海淀区', '丰台区', '石景山区', '通州区', '昌平区', '顺义区', '大兴区'],
    recommendedPlace: { location: '奥林匹克森林公园南门', district: '朝阳区', longitude: 116.392891, latitude: 40.01512 },
  },
}

export const supportedCities = Object.keys(cityConfigs) as SupportedCity[]

export function getCityConfig(city?: string): CityConfig {
  return cityConfigs[city as SupportedCity] ?? cityConfigs.杭州
}
