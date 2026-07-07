import React, { useRef } from 'react';
import { View, Text, StyleSheet, FlatList } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  runOnJS,
  interpolate,
} from 'react-native-reanimated';
import { useAppTheme } from '@/contexts/ThemeContext';

const DASHBOARD_HEIGHT = 180;
const HANDLE_HEIGHT = 28;

type Project = {
  id: string;
  name: string;
  progress: `${number}%`;
};

const projects: Project[] = [
  { id: '1', name: 'NeuralTask Core', progress: '75%' },
  { id: '2', name: 'LiteRT Integration', progress: '40%' },
];

export default function Dashboard({
  isExpanded,
  onToggle,
}: {
  isExpanded: boolean;
  onToggle: (val: boolean) => void;
}) {
  const { colors } = useAppTheme();
  const height = useSharedValue(DASHBOARD_HEIGHT);
  const hasMeasured = useRef(false);

  const tasks = [
    { id: '1', title: 'Review PR #42', time: '10:00 AM', status: 'Pending' },
    { id: '2', title: 'Team Sync', time: '02:00 PM', status: 'Confirmed' },
    { id: '3', title: 'Write unit tests', time: '04:30 PM', status: 'Pending' },
  ];

  React.useEffect(() => {
    if (isExpanded) {
      height.value = withSpring(DASHBOARD_HEIGHT, {
        damping: 15,
        stiffness: 150,
      });
    } else {
      height.value = withSpring(HANDLE_HEIGHT, {
        damping: 15,
        stiffness: 150,
      });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isExpanded]);

  const animatedStyle = useAnimatedStyle(() => {
    return {
      height: height.value,
      opacity: interpolate(height.value, [HANDLE_HEIGHT, DASHBOARD_HEIGHT], [0.6, 1], 'clamp'),
    };
  });

  const panGesture = Gesture.Pan()
// eslint-disable-next-line react-hooks/refs
    .onChange((event) => {
      if (!hasMeasured.current) return;
      const newHeight = isExpanded
        ? DASHBOARD_HEIGHT - event.translationY
        : HANDLE_HEIGHT - event.translationY;
      // eslint-disable-next-line react-hooks/immutability
      height.value = Math.max(HANDLE_HEIGHT, Math.min(DASHBOARD_HEIGHT, newHeight));
    })
    .onEnd((event) => {
      const shouldExpand = event.translationY < -(DASHBOARD_HEIGHT - HANDLE_HEIGHT) / 2;
      // eslint-disable-next-line react-hooks/immutability
      height.value = withSpring(shouldExpand ? DASHBOARD_HEIGHT : HANDLE_HEIGHT);
      runOnJS(onToggle)(shouldExpand);
    });

  const renderTask = ({ item }: { item: typeof tasks[0] }) => (
    <View style={[styles.taskItem, { borderBottomColor: colors.backgroundElement }]}>
      <View>
        <Text style={[styles.taskTitle, { color: colors.text }]}>{item.title}</Text>
        <Text style={[styles.taskTime, { color: colors.textSecondary }]}>{item.time}</Text>
      </View>
      <Text style={[styles.taskStatus, { color: colors.textSecondary }]}>{item.status}</Text>
    </View>
  );

  const renderProject = ({ item }: { item: Project }) => (
    <View style={[styles.projectItem, { borderBottomColor: colors.backgroundElement }]}>
      <Text style={[styles.projectName, { color: colors.text }]}>{item.name}</Text>
      <View style={styles.progressRow}>
        <View style={[styles.progressBar, { backgroundColor: colors.backgroundElement }]}>
          <View style={[styles.progressFill, { width: item.progress }]} />
        </View>
        <Text style={[styles.progressText, { color: colors.textSecondary }]}>{item.progress}</Text>
      </View>
    </View>
  );

  return (
    <GestureDetector gesture={panGesture}>
      <Animated.View
        style={[
          styles.container,
          animatedStyle,
          { backgroundColor: colors.background, borderBottomColor: colors.backgroundElement },
        ]}
      >
        <View style={styles.handleRow}>
          <View style={[styles.handle, { backgroundColor: colors.text }]} />
        </View>

        <View style={styles.contentRow}>
          <View style={[styles.card, { backgroundColor: colors.backgroundElement }]}>
            <Text style={[styles.cardTitle, { color: colors.text }]}>Today&apos;s Tasks</Text>
            <FlatList
              data={tasks}
              renderItem={renderTask}
              keyExtractor={(item) => item.id}
              style={styles.list}
              scrollEnabled={false}
            />
          </View>

          <View style={[styles.card, { backgroundColor: colors.backgroundElement }]}>
            <Text style={[styles.cardTitle, { color: colors.text }]}>Active Projects</Text>
            <FlatList
              data={projects}
              renderItem={renderProject}
              keyExtractor={(item) => item.id}
              style={styles.list}
              scrollEnabled={false}
            />
          </View>
        </View>
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
    borderBottomWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  handleRow: {
    height: HANDLE_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    opacity: 0.4,
  },
  contentRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingBottom: 16,
    gap: 12,
    flex: 1,
  },
  card: {
    flex: 1,
    borderRadius: 16,
    padding: 12,
    overflow: 'hidden',
  },
  cardTitle: {
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
  },
  list: {
    flex: 1,
  },
  taskItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  taskTitle: {
    fontSize: 14,
    fontWeight: '600',
  },
  taskTime: {
    fontSize: 12,
    marginTop: 2,
  },
  taskStatus: {
    fontSize: 12,
    fontWeight: '500',
  },
  projectItem: {
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  projectName: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 6,
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  progressBar: {
    flex: 1,
    height: 6,
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#000',
    borderRadius: 3,
  },
  progressText: {
    fontSize: 12,
    fontWeight: '500',
  },
});
