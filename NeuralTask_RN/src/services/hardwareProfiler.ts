import * as Device from 'expo-device';
import { Platform } from 'react-native';

export interface HardwareProfile {
  platform: string;
  model: string;
  totalMemoryMB: number;
  availableMemoryMB: number | null;
  cpuCount: number | null;
  memoryClass: 'low' | 'medium' | 'high';
  supportsLocalInference: boolean;
}

export async function getHardwareProfile(): Promise<HardwareProfile> {
  const totalMemoryMB = getTotalMemoryMB();
  const availableMemoryMB = getAvailableMemoryMB(totalMemoryMB);
  const memoryClass = determineMemoryClass(totalMemoryMB, availableMemoryMB);

  return {
    platform: Platform.OS,
    model: Device.modelName || Device.deviceName || 'Unknown',
    totalMemoryMB,
    availableMemoryMB,
    cpuCount: getCpuCount(),
    memoryClass,
    supportsLocalInference: memoryClass !== 'low' && availableMemoryMB !== null && availableMemoryMB >= 512,
  };
}

function getTotalMemoryMB(): number {
  const ramStr = (Device as any).deviceMemory;
  if (typeof ramStr === 'number' && ramStr > 0) {
    return ramStr * 1024;
  }
  if (typeof ramStr === 'string') {
    const parsed = parseInt(ramStr, 10);
    if (!Number.isNaN(parsed) && parsed > 0) {
      return parsed * 1024;
    }
  }

  return Platform.select({ android: 4096, ios: 4096, default: 4096 }) ?? 4096;
}

function getAvailableMemoryMB(totalMemoryMB: number): number | null {
  return Math.floor(totalMemoryMB * 0.6);
}

function getCpuCount(): number | null {
  if (Platform.OS === 'android' || Platform.OS === 'ios') {
    const cores = (Device as any).totalMemoryCores;
    if (typeof cores === 'number' && cores > 0) {
      return cores;
    }
  }

  return null;
}

function determineMemoryClass(totalMB: number, availableMB: number | null): 'low' | 'medium' | 'high' {
  const effectiveAvailable = availableMB ?? totalMB;

  if (effectiveAvailable < 1024) {
    return 'low';
  }
  if (effectiveAvailable < 3072) {
    return 'medium';
  }
  return 'high';
}
