import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, SafeAreaView, TouchableOpacity, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAppTheme } from '@/contexts/ThemeContext';
import * as Notifications from 'expo-notifications';
import * as TaskManager from 'expo-task-manager';
import { registerBackgroundTask } from '@/services/notifications';
import { useAIEngine } from '@/contexts/AIEngineContext';
import SideDrawer from '@/components/SideDrawer';
import Dashboard from '@/components/Dashboard';
import ChatUI from '@/components/ChatUI';

function HomeScreenInner() {
  const insets = useSafeAreaInsets();
  const { colors } = useAppTheme();
  const { messages, isLoading, sendMessage } = useAIEngine();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [dashboardExpanded, setDashboardExpanded] = useState(true);

  useEffect(() => {
    let isMounted = true;

    async function setupNotifications() {
      try {
        const { status: existingStatus } = await Notifications.getPermissionsAsync();
        let finalStatus = existingStatus;

        if (existingStatus !== 'granted') {
          const { status } = await Notifications.requestPermissionsAsync();
          finalStatus = status;
        }

        if (finalStatus !== 'granted') {
          console.warn('[Notifications] Permission not granted for notifications');
          return;
        }

        if (Platform.OS === 'android') {
          await Notifications.setNotificationChannelAsync('default', {
            name: 'default',
            importance: Notifications.AndroidImportance.MAX,
            vibrationPattern: [0, 250, 250, 250],
            lightColor: '#FFFFFF',
          });
        }

        const isBackgroundRegistered = await TaskManager.isTaskRegisteredAsync('neuraltask-background-ai-task');
        if (!isBackgroundRegistered) {
          await registerBackgroundTask();
        }
      } catch (error) {
        console.error('[Notifications] Setup error:', error);
      }
    }

    if (isMounted) {
      setupNotifications();
    }

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: colors.background }]}>
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <TouchableOpacity onPress={() => setDrawerOpen(true)} style={styles.menuButton}>
          <Text style={[styles.menuIcon, { color: colors.text }]}>☰</Text>
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: colors.text }]}>NeuralTask</Text>
        <View style={styles.menuButton} />
      </View>

      <View style={styles.body}>
        <Dashboard
          isExpanded={dashboardExpanded}
          onToggle={setDashboardExpanded}
        />
        <ChatUI messages={messages} onSend={sendMessage} isLoading={isLoading} />
      </View>

      <SideDrawer
        isOpen={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      />
    </SafeAreaView>
  );
}

export default function HomeScreen() {
  return <HomeScreenInner />;
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingBottom: 8,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  menuButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  menuIcon: {
    fontSize: 28,
    fontWeight: '300',
  },
  body: {
    flex: 1,
  },
});
