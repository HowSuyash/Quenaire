import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, type ExportFormat } from '@/lib/api';
import { Questionnaire, Question, Answer, PaginatedResponse } from '@/types/api';

export function useQuestionnaires(params?: { page?: number; size?: number; status?: string; search?: string }) {
  return useQuery({
    queryKey: ['questionnaires', params],
    queryFn: () => api.getQuestionnaires(params),
  });
}

export function useQuestionnaire(id: string) {
  return useQuery({
    queryKey: ['questionnaire', id],
    queryFn: () => api.getQuestionnaire(id),
    enabled: !!id,
  });
}

export function useQuestions(questionnaireId: string, params?: { page?: number; size?: number; status?: string }) {
  return useQuery({
    queryKey: ['questions', questionnaireId, params],
    queryFn: () => api.getQuestions(questionnaireId, params),
    enabled: !!questionnaireId,
  });
}

export function useQuestion(id: string) {
  return useQuery({
    queryKey: ['question', id],
    queryFn: () => api.getQuestion(id),
    enabled: !!id,
  });
}

export function useAnswer(questionId: string) {
  return useQuery({
    queryKey: ['answer', questionId],
    queryFn: () => api.getAnswer(questionId),
    enabled: !!questionId,
  });
}

export function useGenerateAnswer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (questionId: string) => api.generateAnswer(questionId),
    onSuccess: (_, questionId) => {
      queryClient.invalidateQueries({ queryKey: ['answer', questionId] });
      queryClient.invalidateQueries({ queryKey: ['questions'] });
    },
  });
}

export function useUpdateAnswer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ questionId, data }: { questionId: string; data: { content: string; status?: string } }) =>
      api.updateAnswer(questionId, data),
    onSuccess: (_, { questionId }) => {
      queryClient.invalidateQueries({ queryKey: ['answer', questionId] });
      queryClient.invalidateQueries({ queryKey: ['questions'] });
    },
  });
}

export function useApproveAnswer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (questionId: string) => api.approveAnswer(questionId),
    onSuccess: (_, questionId) => {
      queryClient.invalidateQueries({ queryKey: ['answer', questionId] });
      queryClient.invalidateQueries({ queryKey: ['questions'] });
      queryClient.invalidateQueries({ queryKey: ['questionnaire'] });
    },
  });
}

export function useRejectAnswer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ questionId, reason }: { questionId: string; reason: string }) =>
      api.rejectAnswer(questionId, reason),
    onSuccess: (_, { questionId }) => {
      queryClient.invalidateQueries({ queryKey: ['answer', questionId] });
      queryClient.invalidateQueries({ queryKey: ['questions'] });
      queryClient.invalidateQueries({ queryKey: ['questionnaire'] });
    },
  });
}

export function useCreateQuestionnaire() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: FormData) => api.createQuestionnaire(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questionnaires'] });
    },
  });
}

export function useProcessQuestionnaire() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.processQuestionnaire(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['questionnaire', id] });
      queryClient.invalidateQueries({ queryKey: ['questionnaires'] });
    },
  });
}

export function useExportQuestionnaire() {
  return useMutation({
    mutationFn: ({ id, format }: { id: string; format: ExportFormat }) =>
      api.exportQuestionnaire(id, format),
  });
}