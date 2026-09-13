'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn, formatDateTime, getStatusColor } from '@/lib/utils';
import { 
  Plus, 
  Search, 
  Upload, 
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  FileText,
  Trash2,
  Eye
} from 'lucide-react';
import Link from 'next/link';

export default function DocumentsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [uploading, setUploading] = useState(false);
  const [isDragActive, setIsDragActive] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['documents', { page, size, search: debouncedSearch }],
    queryFn: () => api.getDocuments({ page, size, search: debouncedSearch }),
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return api.uploadDocument(formData);
    },
    onMutate: () => setUploading(true),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      setUploading(false);
    },
    onError: () => setUploading(false),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteDocument(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents'] }),
  });

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragActive(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragActive(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      Array.from(e.dataTransfer.files).forEach(file => uploadMutation.mutate(file));
    }
  };

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      Array.from(e.target.files).forEach(file => uploadMutation.mutate(file));
    }
  };

  const handleSearchChange = (value: string) => {
    setSearch(value);
    setTimeout(() => {
      setDebouncedSearch(value);
      setPage(0);
    }, 300);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Documents</h1>
          <p className="text-gray-500 mt-1">Manage your knowledge base documents</p>
        </div>
      </div>

      {/* Upload Zone */}
      <Card>
        <CardContent className="p-6">
          <label
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            className={cn(
              'block border-2 border-dashed rounded-xl p-8 text-center transition-colors cursor-pointer',
              isDragActive ? 'border-primary-500 bg-primary-50' : 'border-gray-300 hover:border-primary-400'
            )}
          >
            <input
              type="file"
              className="hidden"
              multiple
              accept=".pdf,.docx,.xlsx,.txt,.md,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/plain,text/markdown"
              onChange={handleFileInput}
              disabled={uploading}
            />
            <Upload aria-hidden="true" className="h-12 w-12 mx-auto text-gray-500 mb-4" />
            <p className="text-lg font-medium text-gray-900">
              {isDragActive ? 'Drop files here...' : 'Drag & drop documents here, or click to select'}
            </p>
            <p className="text-sm text-gray-500 mt-2">
              Supports PDF, DOCX, XLSX, TXT, MD (max 50MB each)
            </p>
            {uploading && <div className="mt-4 text-primary-600 font-medium">Uploading...</div>}
          </label>
        </CardContent>
      </Card>

      {/* Filters */}
      <Card>
        <CardContent className="p-4 pt-6">
          <div className="flex flex-col sm:flex-row gap-4">
            <div className="flex-1 max-w-md">
              <Input
                placeholder="Search documents..."
                value={search}
                onChange={(e) => handleSearchChange(e.target.value)}
                leftIcon={<Search className="h-4 w-4" />}
              />
            </div>
            <Button variant="outline" size="sm">
              <RefreshCw className="h-4 w-4 mr-1" />
              Refresh
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Documents Table */}
      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="p-8 text-center">
              <RefreshCw className="h-8 w-8 animate-spin text-primary-600 mx-auto mb-2" />
              <p className="text-gray-500">Loading documents...</p>
            </div>
          ) : (data?.content?.length ?? 0) === 0 ? (
            <div className="p-12 text-center">
              <FileText className="h-12 w-12 mx-auto text-gray-300 mb-3" />
              <p className="text-gray-500 mb-4">No documents yet</p>
              <p className="text-sm text-gray-600">Drag & drop files above to get started</p>
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Document</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Size</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Pages</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Updated</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {data?.content?.map((doc) => (
                      <tr key={doc.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4">
                          <Link href={`/dashboard/documents/${doc.id}`} className="block">
                            <p className="font-medium text-gray-900 truncate max-w-xs">{doc.title}</p>
                            <p className="text-sm text-gray-500">{doc.fileName || 'No file'}</p>
                          </Link>
                        </td>
                        <td className="px-6 py-4">
                          <Badge variant="outline" className="text-xs">{doc.sourceType}</Badge>
                        </td>
                        <td className="px-6 py-4 text-gray-900">
                          {doc.fileSize ? `${(doc.fileSize / 1024 / 1024).toFixed(1)} MB` : '—'}
                        </td>
                        <td className="px-6 py-4 text-gray-900">{doc.pageCount || '—'}</td>
                        <td className="px-6 py-4">
                          <Badge className={getStatusColor(doc.status)}>{doc.status}</Badge>
                        </td>
                        <td className="px-6 py-4 text-gray-500">{formatDateTime(doc.updatedAt)}</td>
                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <Link href={`/dashboard/documents/${doc.id}`}>
                              <Button variant="ghost" size="icon" className="h-8 w-8">
                                <Eye className="h-4 w-4" />
                              </Button>
                            </Link>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8 text-red-600 hover:bg-red-50"
                              onClick={() => deleteMutation.mutate(doc.id)}
                              disabled={deleteMutation.isPending}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {data && data.totalPages > 1 && (
                <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-between">
                  <p className="text-sm text-gray-500">
                    Showing {page * size + 1} to {Math.min((page + 1) * size, data.totalElements)} of {data.totalElements} results
                  </p>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={page === 0}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
                      disabled={page >= data.totalPages - 1}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}