import * as FileSystem from 'expo-file-system/legacy';
import { getHardwareProfile, type HardwareProfile } from './hardwareProfiler';

export interface LiteRTModelInfo {
  id: string;
  name: string;
  huggingFaceUrl: string;
  fileName: string;
  sizeMB: number;
  runtime: 'litert';
  task: 'chat' | 'embedding' | 'classification';
  description: string;
}

export interface LiteRTModelDownloadState {
  model: LiteRTModelInfo;
  localUri: string | null;
  downloadedSizeMB: number;
  progress: number;
  status: 'idle' | 'downloading' | 'ready' | 'error';
  error?: string;
}

const MODELS_DIR = `${FileSystem.documentDirectory}litert_models/`;

export async function ensureModelsDirectory(): Promise<string> {
  const dirInfo = await FileSystem.getInfoAsync(MODELS_DIR);
  if (!dirInfo.exists) {
    await FileSystem.makeDirectoryAsync(MODELS_DIR, { intermediates: true });
  }
  return MODELS_DIR;
}

export function buildModelDownloadUri(model: LiteRTModelInfo): string {
  return `${MODELS_DIR}${model.fileName}`;
}

export async function downloadLiteRTModel(
  model: LiteRTModelInfo,
  onProgress?: (progress: number) => void
): Promise<LiteRTModelDownloadState> {
  const state: LiteRTModelDownloadState = {
    model,
    localUri: null,
    downloadedSizeMB: 0,
    progress: 0,
    status: 'idle',
  };

  try {
    await ensureModelsDirectory();
    const destinationUri = buildModelDownloadUri(model);
    const downloadResumable = FileSystem.createDownloadResumable(
      model.huggingFaceUrl,
      destinationUri
    );

    state.status = 'downloading';
    state.localUri = destinationUri;

    const result = await downloadResumable.downloadAsync();

    if (result?.uri) {
      state.localUri = result.uri;
      state.status = 'ready';
      state.progress = 1;
      state.downloadedSizeMB = model.sizeMB;

      if (onProgress) {
        onProgress(1);
      }
    } else {
      throw new Error('Download completed but no URI returned');
    }
  } catch (error) {
    state.status = 'error';
    state.error = error instanceof Error ? error.message : 'Unknown download error';
    state.progress = 0;
  }

  return state;
}

export async function isModelDownloaded(model: LiteRTModelInfo): Promise<boolean> {
  try {
    await ensureModelsDirectory();
    const uri = buildModelDownloadUri(model);
    const info = await FileSystem.getInfoAsync(uri);
    return info.exists && 'size' in info && info.size > 0;
  } catch {
    return false;
  }
}

export async function deleteLiteRTModel(model: LiteRTModelInfo): Promise<boolean> {
  try {
    await ensureModelsDirectory();
    const uri = buildModelDownloadUri(model);
    const info = await FileSystem.getInfoAsync(uri);
    if (info.exists) {
      await FileSystem.deleteAsync(uri, { idempotent: true });
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

export async function loadLiteRTModelLocal(model: LiteRTModelInfo): Promise<boolean> {
  try {
    const downloaded = await isModelDownloaded(model);
    if (!downloaded) {
      return false;
    }

    const uri = buildModelDownloadUri(model);
    const info = await FileSystem.getInfoAsync(uri);
    if (!info.exists || !('size' in info)) {
      return false;
    }

    const profile = await getHardwareProfile();
    if (!profile.supportsLocalInference) {
      console.warn(
        `[LiteRT] Device memory class (${profile.memoryClass}) may not support local inference for ${model.name}.`
      );
    }

    return true;
  } catch (error) {
    console.error('[LiteRT] Failed to load model:', error);
    return false;
  }
}

export async function getRecommendedLiteRTModels(
  task: LiteRTModelInfo['task']
): Promise<LiteRTModelInfo[]> {
  const profile = await getHardwareProfile();
  return getModelCatalog(task, profile);
}

function getModelCatalog(
  task: LiteRTModelInfo['task'],
  profile: HardwareProfile
): LiteRTModelInfo[] {
  const catalog: LiteRTModelInfo[] = [
    {
      id: 'mobilenet_v4_litert',
      name: 'MobileNet V4 (LiteRT)',
      huggingFaceUrl: 'https://huggingface.co/google/mobilenet_v4_models/resolve/main/mobilenet_v4_balanced_224.litert.task',
      fileName: 'mobilenet_v4_balanced_224.litert.task',
      sizeMB: 10,
      runtime: 'litert',
      task: 'classification',
      description: 'MobileNet V4 via LiteRT Task Library, optimized for mobile image classification.',
    },
    {
      id: 'gemma_2b_litert',
      name: 'Gemma 2B (LiteRT)',
      huggingFaceUrl: 'https://huggingface.co/google/gemma-2b-litert/resolve/main/gemma_2b_litert.task',
      fileName: 'gemma_2b_litert.task',
      sizeMB: 1300,
      runtime: 'litert',
      task: 'chat',
      description: 'Gemma 2B packaged for LiteRT runtime with optimized workload partitioning.',
    },
    {
      id: 'all_mini_l6_litert',
      name: 'All MiniLM L6 V2 (LiteRT)',
      huggingFaceUrl: 'https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2-litert/resolve/main/all_mini_l6_litert.task',
      fileName: 'all_mini_l6_litert.task',
      sizeMB: 28,
      runtime: 'litert',
      task: 'embedding',
      description: 'Compact sentence embedding runner using the LiteRT Task Library.',
    },
  ];

  const filtered = catalog.filter((m) => m.task === task);

  if (profile.memoryClass === 'low') {
    return filtered.filter((m) => m.sizeMB <= 100);
  }

  if (profile.memoryClass === 'medium') {
    return filtered.filter((m) => m.sizeMB <= 500);
  }

  return filtered;
}
