// Quenaire Side Panel Controller

const DEFAULT_API_BASE = 'http://localhost:8080/api';
const AUTO_REFRESH_MS = 15000;

class SidePanelController {
  constructor() {
    this.apiBaseUrl = DEFAULT_API_BASE;
    this.questions = [];
    this.stats = { detected: 0, filled: 0, highConf: 0, needsReview: 0 };
    this.refreshTimer = null;
    this.init();
  }

  async init() {
    await this.loadSettings();
    this.bindEvents();
    await this.refreshData();
    this.startAutoRefresh();
  }

  async loadSettings() {
    const settings = await chrome.storage.sync.get(['apiBaseUrl', 'autoFill', 'showConfidence']);
    this.apiBaseUrl = settings.apiBaseUrl || DEFAULT_API_BASE;
    document.getElementById('apiUrl').value = this.apiBaseUrl;
    document.getElementById('autoFill').checked = settings.autoFill !== false;
    document.getElementById('showConfidence').checked = settings.showConfidence !== false;
  }

  bindEvents() {
    document.getElementById('btnFillAll').addEventListener('click', () => this.fillAll());
    document.getElementById('btnSubmit').addEventListener('click', () => this.submitToQuenaire());
    document.getElementById('btnRefresh').addEventListener('click', () => this.refreshData());
    document.getElementById('btnSaveSettings').addEventListener('click', () => this.saveSettings());

    document.getElementById('apiUrl').addEventListener('change', (e) => {
      this.apiBaseUrl = e.target.value;
    });

    document.getElementById('autoFill').addEventListener('change', (e) => {
      chrome.storage.sync.set({ autoFill: e.target.checked });
    });

    document.getElementById('showConfidence').addEventListener('change', (e) => {
      chrome.storage.sync.set({ showConfidence: e.target.checked });
      this.renderQuestionList();
    });

    // Delegated so the handler survives every re-render of the list.
    document.getElementById('questionList').addEventListener('click', (e) => {
      const button = e.target.closest('[data-action="fill"]');
      if (!button) return;
      this.fillQuestion(Number(button.dataset.index), button);
    });
  }

  startAutoRefresh() {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    this.refreshTimer = setInterval(() => this.refreshData(), AUTO_REFRESH_MS);
  }

  async refreshData() {
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

      if (!tab || !tab.id) {
        this.showNoTabMessage();
        return;
      }

      const response = await chrome.tabs.sendMessage(tab.id, { action: 'getQuestions' });

      if (response && Array.isArray(response.questions)) {
        this.updateUI(response.questions);
      } else {
        this.showNoQuestionsMessage();
      }
    } catch (error) {
      // sendMessage rejects when no content script is present on the page.
      console.debug('Refresh failed:', error);
      this.showContentScriptError();
    }
  }

  updateUI(questions) {
    this.questions = questions;

    this.stats.detected = questions.length;
    this.stats.filled = questions.filter(q => q.filled).length;
    this.stats.highConf = questions.filter(q => q.confidence >= 90).length;
    this.stats.needsReview = questions.filter(q => q.confidence != null && q.confidence < 70).length;

    this.updateStats();
    this.renderQuestionList();
    this.updateConnectionStatus(true);

    document.getElementById('btnSubmit').disabled = this.stats.filled === 0;
  }

  updateStats() {
    document.getElementById('statDetected').textContent = this.stats.detected;
    document.getElementById('statFilled').textContent = this.stats.filled;
    document.getElementById('statHighConf').textContent = this.stats.highConf;
    document.getElementById('statNeedsReview').textContent = this.stats.needsReview;
  }

  renderQuestionList() {
    const list = document.getElementById('questionList');

    if (this.questions.length === 0) {
      this.showNoQuestionsMessage();
      return;
    }

    const showConfidence = document.getElementById('showConfidence').checked;

    list.innerHTML = this.questions.map((q, index) => {
      const text = this.escapeHtml((q.questionText || 'Unknown question').substring(0, 100));
      const confidenceClass =
        q.confidence >= 90 ? 'confidence-high' :
        q.confidence >= 70 ? 'confidence-medium' : 'confidence-low';

      const confidenceBadge = showConfidence && q.confidence != null
        ? `<span class="${confidenceClass}">${q.confidence}%</span>`
        : '';

      const filledBadge = q.filled
        ? '<span class="badge" style="background:#dcfce7;color:#166534;">Filled</span>'
        : '';

      return `
        <div class="question-item" data-index="${index}">
          <div class="question-info">
            <div class="question-text">${text}</div>
            <div class="question-meta">
              <span class="badge badge-type">${this.escapeHtml(q.type || 'unknown')}</span>
              <span class="badge badge-platform">${this.escapeHtml(q.platform || 'generic')}</span>
              ${confidenceBadge}
              ${filledBadge}
            </div>
          </div>
          <button class="btn btn-primary" style="padding:6px 12px;font-size:11px;"
                  data-action="fill" data-index="${index}">
            ${q.filled ? 'Refill' : 'Fill'}
          </button>
        </div>
      `;
    }).join('');
  }

  async fillAll() {
    const btn = document.getElementById('btnFillAll');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Filling...';

    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.id) {
        // The content script fills sequentially and rate-limits itself; doing it
        // there in one call avoids a message round-trip per question.
        await chrome.tabs.sendMessage(tab.id, { action: 'fillAll' });
      }
    } catch (error) {
      console.error('Fill all failed:', error);
    }

    btn.disabled = false;
    btn.innerHTML = original;
    await this.refreshData();
  }

  async fillQuestion(index, button) {
    const question = this.questions[index];
    if (!question) return;

    if (button) {
      button.disabled = true;
      button.textContent = '...';
    }

    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.id) {
        await chrome.tabs.sendMessage(tab.id, {
          action: 'fillQuestion',
          questionId: question.id
        });
      }
    } catch (error) {
      console.error('Fill failed:', error);
    }

    await this.refreshData();
  }

  async submitToQuenaire() {
    const btn = document.getElementById('btnSubmit');
    const result = document.getElementById('submitResult');
    const original = btn.innerHTML;

    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Submitting...';
    result.textContent = '';

    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab?.id) throw new Error('No active tab');

      const response = await chrome.tabs.sendMessage(tab.id, { action: 'submitToQuenaire' });

      if (response?.success) {
        result.style.color = '#38a169';
        result.textContent = response.message || `Submitted ${response.submitted} answers.`;
      } else {
        result.style.color = '#e53e3e';
        result.textContent = response?.error || 'Submit failed.';
      }
    } catch (error) {
      result.style.color = '#e53e3e';
      result.textContent = `Submit failed: ${error.message}`;
    }

    btn.disabled = false;
    btn.innerHTML = original;
  }

  async saveSettings() {
    const apiUrl = document.getElementById('apiUrl').value;
    await chrome.storage.sync.set({ apiBaseUrl: apiUrl });
    this.apiBaseUrl = apiUrl;

    const btn = document.getElementById('btnSaveSettings');
    const originalText = btn.textContent;
    btn.textContent = 'Saved!';
    btn.style.background = '#48bb78';
    setTimeout(() => {
      btn.textContent = originalText;
      btn.style.background = '';
    }, 2000);
  }

  updateConnectionStatus(connected) {
    const badge = document.getElementById('connectionStatus');
    badge.textContent = connected ? 'Connected' : 'Disconnected';
    badge.className = connected ? 'status-badge connected' : 'status-badge disconnected';
  }

  showNoTabMessage() {
    this.showPlaceholder(
      'Please navigate to a questionnaire page (OneTrust, Whistic, Loopio, etc.) to use Quenaire.'
    );
    this.updateConnectionStatus(false);
  }

  showContentScriptError() {
    this.showPlaceholder('Content script not loaded. Refresh the page and try again.');
    this.updateConnectionStatus(false);
  }

  showNoQuestionsMessage() {
    this.showPlaceholder(
      'No questions detected on this page. Click "Re-detect Questions" or navigate to a questionnaire page.'
    );
  }

  showError(message) {
    document.getElementById('questionList').innerHTML =
      `<div style="padding: 24px; text-align: center; color: #e53e3e;">${this.escapeHtml(message)}</div>`;
  }

  showPlaceholder(message) {
    document.getElementById('questionList').innerHTML =
      `<div style="padding: 24px; text-align: center; color: #a0aec0;">${this.escapeHtml(message)}</div>`;
  }

  escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  new SidePanelController();
});
