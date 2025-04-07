import { useState } from 'react';
import { Box, Text, Button, Group, Card, Title } from '@mantine/core';
import { FiRefreshCw } from 'react-icons/fi';

const tips = [
  {
    title: "Set App Limits",
    content: "Use built-in screen time features to set daily limits for specific apps. When you reach your limit, the app will notify you."
  },
  {
    title: "Create No-Phone Zones",
    content: "Designate certain areas in your home (like the bedroom or dining table) as phone-free zones to reduce mindless scrolling."
  },
  {
    title: "Use Grayscale Mode",
    content: "Switch your phone to grayscale mode. The lack of color makes apps less engaging and can help reduce screen time."
  },
  {
    title: "Practice the 20-20-20 Rule",
    content: "Every 20 minutes, look at something 20 feet away for 20 seconds. This helps reduce eye strain and reminds you to take breaks."
  },
  {
    title: "Schedule Digital Detox",
    content: "Set aside specific times during the day for a digital detox. Start with 30 minutes and gradually increase the duration."
  },
  {
    title: "Turn Off Notifications",
    content: "Disable non-essential notifications to reduce the urge to constantly check your phone."
  },
  {
    title: "Use a Physical Alarm Clock",
    content: "Instead of using your phone as an alarm, use a traditional alarm clock to avoid checking your phone first thing in the morning."
  },
  {
    title: "Implement the 5-Minute Rule",
    content: "Before opening an app, wait 5 minutes. Often, the urge to check will pass, and you'll realize you don't need to use it."
  },
  {
    title: "Try the Pomodoro Technique",
    content: "Work in focused 25-minute intervals followed by 5-minute breaks. This helps maintain productivity while ensuring regular screen breaks."
  },
  {
    title: "Curate Your Social Media",
    content: "Unfollow accounts that trigger negative emotions or excessive scrolling. Follow accounts that inspire and educate instead."
  },
  {
    title: "Use Airplane Mode",
    content: "Enable airplane mode during focused work or family time to eliminate digital distractions completely."
  },
  {
    title: "Practice Mindful Scrolling",
    content: "Before opening social media, ask yourself: 'What am I looking for?' This helps prevent mindless browsing."
  },
  {
    title: "Create a Digital Sunset",
    content: "Set a specific time each evening to stop using screens. This helps improve sleep quality and mental well-being."
  },
  {
    title: "Use a Website Blocker",
    content: "Install browser extensions that block distracting websites during work hours or specific times of the day."
  },
  {
    title: "Implement the Two-Minute Rule",
    content: "If a task takes less than two minutes, do it immediately instead of adding it to your digital to-do list."
  },
  {
    title: "Try Digital Minimalism",
    content: "Regularly audit your apps and digital tools. Keep only those that add significant value to your life."
  },
  {
    title: "Use Focus Mode",
    content: "Enable focus or do-not-disturb mode during important tasks to minimize interruptions and maintain concentration."
  },
  {
    title: "Practice Digital Decluttering",
    content: "Regularly clean up your digital spaces - organize files, delete unused apps, and clear browser tabs."
  },
  {
    title: "Set Up a Charging Station",
    content: "Create a designated charging area away from your bed to prevent late-night screen time."
  },
  {
    title: "Use the 1-3-5 Rule",
    content: "Each day, focus on 1 big task, 3 medium tasks, and 5 small tasks. This helps maintain focus and reduce digital overwhelm."
  },
  {
    title: "Try the 10-3-2-1-0 Rule",
    content: "No screens 10 minutes before bed, 3 hours before bed, 2 hours before bed, 1 hour before bed, and 0 screens in bed."
  },
  {
    title: "Practice Digital Sabbath",
    content: "Take one day a week to disconnect from digital devices completely. Use this time for offline activities and reflection."
  },
  {
    title: "Use Screen Time Analytics",
    content: "Regularly review your screen time reports to identify patterns and areas for improvement."
  },
  {
    title: "Implement the 80/20 Rule",
    content: "Focus on the 20% of apps that provide 80% of your value. Reduce time spent on less valuable digital activities."
  },
  {
    title: "Create a Digital Morning Routine",
    content: "Start your day with offline activities before checking your phone. This sets a positive tone for the day."
  }
];

const TipsSection = () => {
  const [currentTip, setCurrentTip] = useState(0);

  const getRandomTip = () => {
    let newTip;
    do {
      newTip = Math.floor(Math.random() * tips.length);
    } while (newTip === currentTip);
    setCurrentTip(newTip);
  };

  return (
    <Box mt="xl">
      <Card
        shadow="sm"
        padding="lg"
        radius="md"
        withBorder
        style={{
          background: 'rgba(0, 0, 32, 0.8)',
          borderColor: '#FF00FF',
        }}
      >
        <Group justify="space-between" mb="md">
          <Title order={3} style={{ color: '#00FFFF' }}>
            Digital Wellness Tip
          </Title>
          <Button
            variant="light"
            color="cyan"
            radius="md"
            size="sm"
            onClick={getRandomTip}
            leftSection={<FiRefreshCw />}
            style={{
              background: 'linear-gradient(45deg, #00FFFF, #FF00FF)',
              color: '#000',
              fontWeight: 'bold',
            }}
          >
            New Tip
          </Button>
        </Group>
        <Text size="lg" fw={500} style={{ color: '#FF00FF', marginBottom: '0.5rem' }}>
          {tips[currentTip].title}
        </Text>
        <Text size="md" style={{ color: '#f0f0f0' }}>
          {tips[currentTip].content}
        </Text>
      </Card>
    </Box>
  );
};

export default TipsSection; 