import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

export function useJiraStatus() {
  return useQuery({
    queryKey: ['integrations', 'jira', 'status'],
    queryFn: () => api.getJiraStatus(),
  });
}

export function useCreateJiraIssue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (questionnaireId: string) => api.createJiraIssue(questionnaireId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'jira'] });
    },
  });
}

export function useConfluenceStatus() {
  return useQuery({
    queryKey: ['integrations', 'confluence', 'status'],
    queryFn: () => api.getConfluenceStatus(),
  });
}

export function useCreateConfluencePage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (questionnaireId: string) => api.createConfluencePage(questionnaireId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'confluence'] });
    },
  });
}

export function useGitHubStatus() {
  return useQuery({
    queryKey: ['integrations', 'github', 'status'],
    queryFn: () => api.getGitHubStatus(),
  });
}

export function useCreateGitHubIssue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (questionnaireId: string) => api.createGitHubIssue(questionnaireId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'github'] });
    },
  });
}

export function useSlackStatus() {
  return useQuery({
    queryKey: ['integrations', 'slack', 'status'],
    queryFn: () => api.getSlackStatus(),
  });
}

export function useSendSlackNotification() {
  return useMutation({
    mutationFn: ({ questionnaireId, channel }: { questionnaireId: string; channel?: string }) =>
      api.sendSlackNotification(questionnaireId, channel),
  });
}