import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { useAppTheme } from '@/contexts/ThemeContext';
import { type ChatMessage } from '@/contexts/AIEngineContext';

export default function ChatUI({
  messages,
  onSend,
  isLoading = false,
}: {
  messages: ChatMessage[];
  onSend: (text: string) => void;
  isLoading?: boolean;
}) {
  const { colors } = useAppTheme();
  const [text, setText] = useState('');
  const flatListRef = useRef<FlatList>(null);

  const handleSend = () => {
    if (!text.trim()) return;
    onSend(text.trim());
    setText('');
  };

  const renderMessage = ({ item }: { item: ChatMessage }) => {
    const isUser = item.sender === 'user';
    return (
      <View
        style={[
          styles.messageRow,
          isUser ? styles.userRow : styles.aiRow,
        ]}
      >
        <View
          style={[
            styles.bubble,
            {
              backgroundColor: isUser ? colors.text : colors.backgroundElement,
              borderColor: colors.backgroundElement,
            },
          ]}
        >
          <Text
            style={[
              styles.messageText,
              {
                color: isUser ? colors.background : colors.text,
              },
            ]}
          >
            {item.text}
          </Text>
        </View>
      </View>
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
    >
      <FlatList
        ref={flatListRef}
        data={messages}
        renderItem={renderMessage}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        onContentSizeChange={() => flatListRef.current?.scrollToEnd({ animated: true })}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={[styles.emptyText, { color: colors.textSecondary }]}>
              NeuralTask is ready. How can I help you today?
            </Text>
          </View>
        }
        ListFooterComponent={
          isLoading ? (
            <View style={styles.loadingRow}>
              <ActivityIndicator size="small" color={colors.textSecondary} />
              <Text style={[styles.loadingText, { color: colors.textSecondary }]}>
                Thinking...
              </Text>
            </View>
          ) : null
        }
      />

      <View style={[styles.inputBar, { borderTopColor: colors.backgroundElement }]}>
        <TextInput
          style={[styles.input, { color: colors.text, backgroundColor: colors.backgroundElement }]}
          placeholder="Message NeuralTask..."
          placeholderTextColor={colors.textSecondary}
          value={text}
          onChangeText={setText}
          onSubmitEditing={handleSend}
          returnKeyType="send"
          editable={!isLoading}
        />
        <TouchableOpacity
          style={[
            styles.sendButton,
            { backgroundColor: isLoading ? colors.backgroundElement : colors.text },
          ]}
          onPress={handleSend}
          disabled={isLoading}
        >
          <Text
            style={[
              styles.sendText,
              { color: isLoading ? colors.textSecondary : colors.background },
            ]}
          >
            ➤
          </Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  listContent: {
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 80,
  },
  emptyText: {
    fontSize: 16,
    fontWeight: '500',
    textAlign: 'center',
  },
  messageRow: {
    marginBottom: 12,
  },
  userRow: {
    alignItems: 'flex-end',
  },
  aiRow: {
    alignItems: 'flex-start',
  },
  bubble: {
    maxWidth: '80%',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1,
  },
  messageText: {
    fontSize: 15,
    lineHeight: 20,
  },
  loadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  loadingText: {
    fontSize: 13,
    fontWeight: '500',
  },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 10,
    borderTopWidth: 1,
  },
  input: {
    flex: 1,
    borderRadius: 24,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 15,
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendText: {
    fontSize: 18,
    fontWeight: '700',
  },
});
