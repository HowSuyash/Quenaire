'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/lib/utils';
import { 
  Save, 
  CheckCircle, 
  XCircle, 
  RefreshCw,
  AlertCircle,
  Info,
  ExternalLink,
  Loader2,
  Plus
} from 'lucide-react';
import { useToast } from '@/hooks/useToast';

const integrations = [
  {
    id: 'jira',
    name: 'Jira',
    description: 'Create issues and sub-tasks for questionnaires',
    icon: '🔵',
    statusKey: 'jira',
    configFields: [
      { key: 'baseUrl', label: 'Base URL', type: 'url', placeholder: 'https://your-domain.atlassian.net' },
      { key: 'username', label: 'Username/Email', type: 'email', placeholder: 'user@company.com' },
      { key: 'apiToken', label: 'API Token', type: 'password', placeholder: '••••••••' },
      { key: 'projectKey', label: 'Project Key', type: 'text', placeholder: 'SEC' },
    ],
  },
  {
    id: 'confluence',
    name: 'Confluence',
    description: 'Publish questionnaires and documents as pages',
    icon: '🔵',
    statusKey: 'confluence',
    configFields: [
      { key: 'baseUrl', label: 'Base URL', type: 'url', placeholder: 'https://your-domain.atlassian.net/wiki' },
      { key: 'username', label: 'Username/Email', type: 'email', placeholder: 'user@company.com' },
      { key: 'apiToken', label: 'API Token', type: 'password', placeholder: '••••••••' },
      { key: 'spaceKey', label: 'Space Key', type: 'text', placeholder: 'SEC' },
    ],
  },
  {
    id: 'github',
    name: 'GitHub',
    description: 'Create issues and track questionnaire progress',
    icon: '⚫',
    statusKey: 'github',
    configFields: [
      { key: 'token', label: 'Personal Access Token', type: 'password', placeholder: 'ghp_••••••••' },
      { key: 'owner', label: 'Owner/Organization', type: 'text', placeholder: 'your-org' },
      { key: 'repository', label: 'Repository', type: 'text', placeholder: 'security-questionnaires' },
      { key: 'baseUrl', label: 'API Base URL (optional)', type: 'url', placeholder: 'https://api.github.com' },
    ],
  },
  {
    id: 'slack',
    name: 'Slack',
    description: 'Real-time notifications and approval workflows',
    icon: '🟣',
    statusKey: 'slack',
    configFields: [
      { key: 'botToken', label: 'Bot Token', type: 'password', placeholder: 'xoxb-••••••••' },
      { key: 'signingSecret', label: 'Signing Secret', type: 'password', placeholder: '••••••••' },
      { key: 'defaultChannel', label: 'Default Channel', type: 'text', placeholder: '#security-questionnaires' },
    ],
  },
];

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [activeTab, setActiveTab] = useState<'integrations' | 'general'>('integrations');
  const [configs, setConfigs] = useState<Record<string, Record<string, string>>>({});
  const [testing, setTesting] = useState<string | null>(null);

  const { data: jiraStatus } = useQuery({ queryKey: ['integrations', 'jira', 'status'], queryFn: () => api.getJiraStatus() });
  const { data: confluenceStatus } = useQuery({ queryKey: ['integrations', 'confluence', 'status'], queryFn: () => api.getConfluenceStatus() });
  const { data: githubStatus } = useQuery({ queryKey: ['integrations', 'github', 'status'], queryFn: () => api.getGitHubStatus() });
  const { data: slackStatus } = useQuery({ queryKey: ['integrations', 'slack', 'status'], queryFn: () => api.getSlackStatus() });

  const statusMap = {
    jira: jiraStatus?.configured,
    confluence: confluenceStatus?.configured,
    github: githubStatus?.configured,
    slack: slackStatus?.configured,
  };

  const testConnectionMutation = useMutation({
    mutationFn: async (integrationId: string) => {
      // In a real app, call the test endpoint
      await new Promise(resolve => setTimeout(resolve, 1000));
      return true;
    },
    onMutate: (integrationId) => setTesting(integrationId),
    onSuccess: (_, integrationId) => {
      setTesting(null);
      toast({ title: 'Connection successful', description: `${integrationId} is properly configured`, variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['integrations', integrationId, 'status'] });
    },
    onError: (_, integrationId) => {
      setTesting(null);
      toast({ title: 'Connection failed', description: `Failed to connect to ${integrationId}`, variant: 'destructive' });
    },
  });

  const saveConfigMutation = useMutation({
    mutationFn: async ({ integrationId, config }: { integrationId: string; config: Record<string, string> }) => {
      // In a real app, call the save endpoint
      await new Promise(resolve => setTimeout(resolve, 500));
      return true;
    },
    onSuccess: (_, { integrationId }) => {
      toast({ title: 'Configuration saved', description: `${integrationId} settings updated`, variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['integrations', integrationId, 'status'] });
    },
    onError: (_, { integrationId }) => {
      toast({ title: 'Save failed', description: `Failed to save ${integrationId} settings`, variant: 'destructive' });
    },
  });

  const handleSave = (integrationId: string) => {
    saveConfigMutation.mutate({ integrationId, config: configs[integrationId] || {} });
  };

  const handleTest = (integrationId: string) => {
    testConnectionMutation.mutate(integrationId);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Settings</h1>
        <p className="text-gray-500 mt-1">Configure your account and integrations</p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-gray-200">
        <button
          onClick={() => setActiveTab('integrations')}
          className={cn(
            'px-6 py-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'integrations' 
              ? 'border-primary-600 text-primary-600' 
              : 'border-transparent text-gray-500 hover:text-gray-700'
          )}
        >
          Integrations
        </button>
        <button
          onClick={() => setActiveTab('general')}
          className={cn(
            'px-6 py-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === 'general' 
              ? 'border-primary-600 text-primary-600' 
              : 'border-transparent text-gray-500 hover:text-gray-700'
          )}
        >
          General
        </button>
      </div>

      {activeTab === 'integrations' && (
        <div className="space-y-6">
          {integrations.map((integration) => {
            const isConfigured = statusMap[integration.statusKey as keyof typeof statusMap];
            const config = configs[integration.id] || {};
            const isTesting = testing === integration.id;

            return (
              <Card key={integration.id}>
                <CardHeader className="flex flex-row items-center justify-between">
                  <div className="flex items-center gap-4">
                    <span className="text-3xl">{integration.icon}</span>
                    <div>
                      <CardTitle className="flex items-center gap-2">
                        {integration.name}
                        {isConfigured && (
                          <Badge variant="success" className="flex items-center gap-1">
                            <CheckCircle className="h-3 w-3" />
                            Connected
                          </Badge>
                        )}
                        {!isConfigured && (
                          <Badge variant="secondary" className="flex items-center gap-1">
                            <XCircle className="h-3 w-3" />
                            Not configured
                          </Badge>
                        )}
                      </CardTitle>
                      <CardDescription>{integration.description}</CardDescription>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                    {integration.configFields.map((field) => (
                      <Input
                        key={field.key}
                        label={field.label}
                        type={field.type}
                        placeholder={field.placeholder}
                        value={config[field.key] || ''}
                        onChange={(e) => setConfigs(prev => ({
                          ...prev,
                          [integration.id]: { ...prev[integration.id], [field.key]: e.target.value }
                        }))}
                      />
                    ))}
                  </div>
                  <div className="flex items-center gap-4 pt-4 border-t border-gray-200">
                    <Button
                      onClick={() => handleSave(integration.id)}
                      disabled={saveConfigMutation.isPending}
                    >
                      {saveConfigMutation.isPending ? (
                        <Loader2 className="h-4 w-4 animate-spin mr-2" />
                      ) : (
                        <Save className="h-4 w-4 mr-2" />
                      )}
                      Save Configuration
                    </Button>
                    {isConfigured && (
                      <Button
                        variant="outline"
                        onClick={() => handleTest(integration.id)}
                        disabled={isTesting}
                      >
                        {isTesting ? (
                          <Loader2 className="h-4 w-4 animate-spin mr-2" />
                        ) : (
                          <RefreshCw className="h-4 w-4 mr-2" />
                        )}
                        Test Connection
                      </Button>
                    )}
                    {!isConfigured && (
                      <div className="flex items-center gap-2 text-sm text-gray-500 ml-auto">
                        <Info className="h-4 w-4" />
                        <span>Configure all fields to enable</span>
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {activeTab === 'general' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>General Settings</CardTitle>
              <CardDescription>Configure application-wide settings</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <h3 className="text-lg font-medium text-gray-900 mb-4">Appearance</h3>
                  <div className="space-y-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">Theme</label>
                      <Select
                        value="system"
                        onChange={() => {}}
                        options={[
                          { value: 'light', label: 'Light' },
                          { value: 'dark', label: 'Dark' },
                          { value: 'system', label: 'System' },
                        ]}
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">Language</label>
                      <Select
                        value="en"
                        onChange={() => {}}
                        options={[
                          { value: 'en', label: 'English' },
                          { value: 'es', label: 'Spanish' },
                          { value: 'fr', label: 'French' },
                          { value: 'de', label: 'German' },
                        ]}
                      />
                    </div>
                  </div>
                </div>
                <div>
                  <h3 className="text-lg font-medium text-gray-900 mb-4">Notifications</h3>
                  <div className="space-y-4">
                    <label className="flex items-center gap-3">
                      <input type="checkbox" className="rounded border-gray-300 text-primary-600 focus:ring-primary-500" defaultChecked />
                      <span className="text-sm text-gray-700">Email notifications for questionnaire updates</span>
                    </label>
                    <label className="flex items-center gap-3">
                      <input type="checkbox" className="rounded border-gray-300 text-primary-600 focus:ring-primary-500" defaultChecked />
                      <span className="text-sm text-gray-700">Slack notifications for approval requests</span>
                    </label>
                    <label className="flex items-center gap-3">
                      <input type="checkbox" className="rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                      <span className="text-sm text-gray-700">Weekly digest emails</span>
                    </label>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>API Access</CardTitle>
              <CardDescription>Manage API keys for programmatic access</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-gray-900">No API keys created yet</p>
                  <p className="text-sm text-gray-500">Create an API key to access Quenaire programmatically</p>
                </div>
                <Button variant="outline">
                  <Plus className="h-4 w-4 mr-2" />
                  Create API Key
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}