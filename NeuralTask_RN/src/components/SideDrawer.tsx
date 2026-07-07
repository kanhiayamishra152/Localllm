import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Switch, TextInput, TouchableOpacity, ScrollView } from 'react-native';
import { GestureDetector, Gesture } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  interpolate,
  Extrapolation,
} from 'react-native-reanimated';
import { useAppTheme } from '@/contexts/ThemeContext';
import {
  saveIntegrationKey,
  getIntegrationKey,
  type IntegrationType,
} from '@/services/secureStore';

const DRAWER_WIDTH = 340;

type ConnStatus = 'connected' | 'disconnected';

export default function SideDrawer({
  isOpen,
  onClose,
}: {
  isOpen: boolean;
  onClose: () => void;
}) {
  const { colors, mode, setMode } = useAppTheme();
  const translateX = useSharedValue(-DRAWER_WIDTH);

  const [githubToken, setGithubToken] = useState('');
  const [gmailClientId, setGmailClientId] = useState('');
  const [gmailClientSecret, setGmailClientSecret] = useState('');
  const [telegramBotToken, setTelegramBotToken] = useState('');
  const [telegramChatId, setTelegramChatId] = useState('');
  const [whatsappToken, setWhatsappToken] = useState('');
  const [whatsappPhoneId, setWhatsappPhoneId] = useState('');

  const [statuses, setStatuses] = useState<Record<IntegrationType, ConnStatus>>({
    github: 'disconnected',
    gmail: 'disconnected',
    telegram: 'disconnected',
    whatsapp: 'disconnected',
  });

  const animatedStyle = useAnimatedStyle(() => {
    return {
      transform: [{ translateX: translateX.value }],
    };
  });

  const backdropStyle = useAnimatedStyle(() => {
    const opacity = interpolate(
      translateX.value,
      [-DRAWER_WIDTH, 0],
      [0, 0.5],
      Extrapolation.CLAMP
    );
    return { opacity };
  });

  useEffect(() => {
    // eslint-disable-next-line react-hooks/immutability
    translateX.value = withSpring(isOpen ? 0 : -DRAWER_WIDTH, {
      damping: 15,
      stiffness: 150,
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  const loadStoredKeys = async () => {
    const gh = await getIntegrationKey('github', 'token');
    const gci = await getIntegrationKey('gmail', 'clientId');
    const gcs = await getIntegrationKey('gmail', 'clientSecret');
    const tg = await getIntegrationKey('telegram', 'botToken');
    const tc = await getIntegrationKey('telegram', 'chatId');
    const wa = await getIntegrationKey('whatsapp', 'whatsappToken');
    const wp = await getIntegrationKey('whatsapp', 'phoneNumberId');

    if (gh) setGithubToken(gh);
    if (gci) setGmailClientId(gci);
    if (gcs) setGmailClientSecret(gcs);
    if (tg) setTelegramBotToken(tg);
    if (tc) setTelegramChatId(tc);
    if (wa) setWhatsappToken(wa);
    if (wp) setWhatsappPhoneId(wp);

    setStatuses({
      github: gh ? 'connected' : 'disconnected',
      gmail: gci ? 'connected' : 'disconnected',
      telegram: tg ? 'connected' : 'disconnected',
      whatsapp: wa ? 'connected' : 'disconnected',
    });
  };

  useEffect(() => {
    if (isOpen) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      loadStoredKeys();
    }
  }, [isOpen]);

  const handleSaveGithub = async () => {
    if (githubToken.trim()) {
      await saveIntegrationKey('github', 'token', githubToken.trim());
      setStatuses((s) => ({ ...s, github: 'connected' }));
    } else {
      setStatuses((s) => ({ ...s, github: 'disconnected' }));
    }
  };

  const handleSaveGmail = async () => {
    if (gmailClientId.trim() && gmailClientSecret.trim()) {
      await saveIntegrationKey('gmail', 'clientId', gmailClientId.trim());
      await saveIntegrationKey('gmail', 'clientSecret', gmailClientSecret.trim());
      setStatuses((s) => ({ ...s, gmail: 'connected' }));
    } else {
      setStatuses((s) => ({ ...s, gmail: 'disconnected' }));
    }
  };

  const handleSaveTelegram = async () => {
    if (telegramBotToken.trim() && telegramChatId.trim()) {
      await saveIntegrationKey('telegram', 'botToken', telegramBotToken.trim());
      await saveIntegrationKey('telegram', 'chatId', telegramChatId.trim());
      setStatuses((s) => ({ ...s, telegram: 'connected' }));
    } else {
      setStatuses((s) => ({ ...s, telegram: 'disconnected' }));
    }
  };

  const handleSaveWhatsapp = async () => {
    if (whatsappToken.trim() && whatsappPhoneId.trim()) {
      await saveIntegrationKey('whatsapp', 'whatsappToken', whatsappToken.trim());
      await saveIntegrationKey('whatsapp', 'phoneNumberId', whatsappPhoneId.trim());
      setStatuses((s) => ({ ...s, whatsapp: 'connected' }));
    } else {
      setStatuses((s) => ({ ...s, whatsapp: 'disconnected' }));
    }
  };

  const panGesture = Gesture.Pan()
    .onChange((event) => {
      // eslint-disable-next-line react-hooks/immutability
      translateX.value = Math.min(0, Math.max(-DRAWER_WIDTH, translateX.value + event.changeX));
    })
    .onEnd(() => {
      if (translateX.value > -DRAWER_WIDTH / 2) {
        // eslint-disable-next-line react-hooks/immutability
        translateX.value = withSpring(0);
      } else {
        translateX.value = withSpring(-DRAWER_WIDTH);
        onClose();
      }
    });

  const tapGesture = Gesture.Tap().onEnd(() => {
    // eslint-disable-next-line react-hooks/immutability
    translateX.value = withSpring(-DRAWER_WIDTH);
    onClose();
  });

  return (
    <>
      <GestureDetector gesture={panGesture}>
        <ScrollView style={[styles.drawerScroll, { backgroundColor: colors.background }]}>
          <Animated.View style={[styles.drawerContent, animatedStyle]}>
            <View style={styles.drawerHeader}>
              <Text style={[styles.drawerTitle, { color: colors.text }]}>NeuralTask</Text>
              <Text style={[styles.drawerSubtitle, { color: colors.textSecondary }]}>Settings</Text>
            </View>

            <View style={styles.drawerSection}>
              <Text style={[styles.sectionLabel, { color: colors.textSecondary }]}>Appearance</Text>
              <View style={styles.row}>
                <Text style={[styles.rowText, { color: colors.text }]}>Dark Mode</Text>
                <Switch
                  value={mode === 'dark'}
                  onValueChange={(val) => setMode(val ? 'dark' : 'light')}
                  trackColor={{ false: colors.backgroundElement, true: colors.text }}
                  thumbColor={colors.background}
                />
              </View>
              <View style={styles.row}>
                <Text style={[styles.rowText, { color: colors.text }]}>Follow System</Text>
                <Switch
                  value={mode === 'auto'}
                  onValueChange={(val) => setMode(val ? 'auto' : (mode === 'dark' ? 'light' : 'dark'))}
                  trackColor={{ false: colors.backgroundElement, true: colors.text }}
                  thumbColor={colors.background}
                />
              </View>
            </View>

            <View style={styles.drawerSection}>
              <Text style={[styles.sectionLabel, { color: colors.textSecondary }]}>Integrations</Text>

              <SettingsField
                label="GitHub Token"
                value={githubToken}
                onChangeText={setGithubToken}
                onSave={handleSaveGithub}
                status={statuses.github}
                colors={colors}
                placeholder="ghp_..."
              />

              <View style={styles.fieldGroup}>
                <Text style={[styles.fieldLabel, { color: colors.textSecondary }]}>Gmail Client ID</Text>
                <TextInput
                  style={[styles.input, { color: colors.text, borderColor: colors.backgroundElement, backgroundColor: colors.backgroundElement }]}
                  value={gmailClientId}
                  onChangeText={setGmailClientId}
                  placeholder="Client ID"
                  placeholderTextColor={colors.textSecondary}
                  autoCapitalize="none"
                />
                <Text style={[styles.fieldLabel, { color: colors.textSecondary }]}>Gmail Client Secret</Text>
                <TextInput
                  style={[styles.input, { color: colors.text, borderColor: colors.backgroundElement, backgroundColor: colors.backgroundElement }]}
                  value={gmailClientSecret}
                  onChangeText={setGmailClientSecret}
                  placeholder="Client Secret"
                  placeholderTextColor={colors.textSecondary}
                  autoCapitalize="none"
                />
                <TouchableOpacity style={[styles.saveButton, { backgroundColor: colors.text }]} onPress={handleSaveGmail}>
                  <Text style={[styles.saveButtonText, { color: colors.background }]}>Save Gmail</Text>
                </TouchableOpacity>
              </View>

              <SettingsField
                label="Telegram Bot Token"
                value={telegramBotToken}
                onChangeText={setTelegramBotToken}
                onSave={handleSaveTelegram}
                status={statuses.telegram}
                colors={colors}
                placeholder="123456:ABC-DEF..."
              />
              <SettingsField
                label="Telegram Chat ID"
                value={telegramChatId}
                onChangeText={setTelegramChatId}
                onSave={handleSaveTelegram}
                status={statuses.telegram}
                colors={colors}
                placeholder="-1001234567890"
              />

              <View style={styles.fieldGroup}>
                <Text style={[styles.fieldLabel, { color: colors.textSecondary }]}>WhatsApp Token</Text>
                <TextInput
                  style={[styles.input, { color: colors.text, borderColor: colors.backgroundElement, backgroundColor: colors.backgroundElement }]}
                  value={whatsappToken}
                  onChangeText={setWhatsappToken}
                  placeholder="EAABwz..."
                  placeholderTextColor={colors.textSecondary}
                  autoCapitalize="none"
                />
                <Text style={[styles.fieldLabel, { color: colors.textSecondary }]}>WhatsApp Phone Number ID</Text>
                <TextInput
                  style={[styles.input, { color: colors.text, borderColor: colors.backgroundElement, backgroundColor: colors.backgroundElement }]}
                  value={whatsappPhoneId}
                  onChangeText={setWhatsappPhoneId}
                  placeholder="1234567890"
                  placeholderTextColor={colors.textSecondary}
                  autoCapitalize="none"
                />
                <TouchableOpacity style={[styles.saveButton, { backgroundColor: colors.text }]} onPress={handleSaveWhatsapp}>
                  <Text style={[styles.saveButtonText, { color: colors.background }]}>Save WhatsApp</Text>
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.drawerSection}>
              <Text style={[styles.sectionLabel, { color: colors.textSecondary }]}>Model</Text>
              <View style={styles.row}>
                <Text style={[styles.rowText, { color: colors.text }]}>Active Model</Text>
                <Text style={[styles.statusText, { color: colors.textSecondary }]}>Cloud API</Text>
              </View>
            </View>
          </Animated.View>
        </ScrollView>
      </GestureDetector>

      {isOpen && (
        <GestureDetector gesture={tapGesture}>
          <Animated.View style={[styles.backdrop, backdropStyle]} />
        </GestureDetector>
      )}
    </>
  );
}

function SettingsField({
  label,
  value,
  onChangeText,
  onSave,
  status,
  colors,
  placeholder,
}: {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  onSave: () => void;
  status: ConnStatus;
  colors: any;
  placeholder?: string;
}) {
  return (
    <View style={styles.fieldGroup}>
      <Text style={[styles.fieldLabel, { color: colors.textSecondary }]}>{label}</Text>
      <TextInput
        style={[styles.input, { color: colors.text, borderColor: colors.backgroundElement, backgroundColor: colors.backgroundElement }]}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textSecondary}
        autoCapitalize="none"
      />
      <TouchableOpacity style={[styles.saveButton, { backgroundColor: colors.text }]} onPress={onSave}>
        <Text style={[styles.saveButtonText, { color: colors.background }]}>Save</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  drawerScroll: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: DRAWER_WIDTH,
    height: '100%',
    zIndex: 10,
  },
  drawerContent: {
    paddingTop: 60,
    paddingHorizontal: 24,
    paddingBottom: 40,
    minHeight: '100%',
  },
  drawerHeader: {
    marginBottom: 32,
    paddingBottom: 24,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#ccc',
  },
  drawerTitle: {
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: -0.5,
  },
  drawerSubtitle: {
    fontSize: 14,
    marginTop: 4,
  },
  drawerSection: {
    marginBottom: 28,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 12,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
  },
  rowText: {
    fontSize: 16,
    fontWeight: '500',
  },
  statusText: {
    fontSize: 13,
  },
  fieldGroup: {
    marginBottom: 12,
  },
  fieldLabel: {
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 4,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    marginBottom: 8,
  },
  saveButton: {
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
    alignItems: 'center',
  },
  saveButtonText: {
    fontSize: 13,
    fontWeight: '600',
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: '#000',
    zIndex: 9,
  },
});
