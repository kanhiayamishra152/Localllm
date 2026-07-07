import React, { createContext, useContext, useState } from 'react';
import { useColorScheme } from 'react-native';
import { Colors } from '@/constants/theme';

type ThemeMode = 'light' | 'dark' | 'auto';

export type ThemeColorScheme = {
  [K in keyof typeof Colors.light]: string;
};

interface ThemeContextType {
  mode: ThemeMode;
  colors: ThemeColorScheme;
  setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme();
  const [mode, setMode] = useState<ThemeMode>('auto');

  const colors = mode === 'auto' ? Colors[systemScheme === 'unspecified' ? 'light' : systemScheme] : Colors[mode];

  return (
    <ThemeContext.Provider value={{ mode, colors, setMode }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useAppTheme must be used within ThemeProvider');
  }
  return context;
}
