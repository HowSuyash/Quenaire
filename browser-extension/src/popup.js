// ComplyAI Popup Controller

class PopupController {
  constructor() {
    this.init();
  }

  async init() {
    this.bindEvents();
    await this.refreshStatus();
  }

  bindEvents() {
    document.getElementById('btnFillAll').addEventListener('click', () => this.fillAll());
    document.getElementById('btnRefresh').addEventListener('click', () => this.refreshStatus());
    document.getElementById('linkSidePanel').addEventListener('click', async (e) => {
      e.preventDefault();
      const window_ = await chrome.windows.getCurrent();
      await chrome.sidePanel.open({ windowId: window_.id });
      window.close();
    });
    document.getElementById('btnSubmit').addEventListener('click', () => this.submitToComplyAI());
    document.getElementById('linkOptions').addEventListener('click', (e) => {
      e.preventDefault();
      chrome.runtime.openOptionsPage();
      window.close();
    });
  }

  async refreshStatus() {
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      
      if (!tab || !tab.id) {
        this.updateConnectionStatus(false);
        this.showNoTabMessage();
        return;
      }

      // Check if content script is loaded
      chrome.tabs.sendMessage(tab.id, { action: 'getQuestions' }, (response) => {
        if (chrome.runtime.lastError) {
          this.updateConnectionStatus(false);
          this.showContentScriptError();
          return;
        }

        if (response && response.questions) {
          this.updateUI(response.questions);
          this.updateConnectionStatus(true);
        } else {
          this.showNoQuestionsMessage();
        }
      });
    } catch (error) {
      console.error('Popup refresh failed:', error);
      this.updateConnectionStatus(false);
    }
  }

  updateUI(questions) {
    const filled = questions.filter(q => q.filled).length;

    document.getElementById('statDetected').textContent = questions.length;
    document.getElementById('statFilled').textContent = filled;
    document.getElementById('statHighConf').textContent =
      questions.filter(q => q.confidence >= 90).length;
    document.getElementById('statNeedsReview').textContent =
      questions.filter(q => q.confidence != null && q.confidence < 70).length;

    // Submitting an empty page just wastes a round-trip.
    document.getElementById('btnSubmit').disabled = filled === 0;

    this.updateConnectionStatus(true);
  }

  async submitToComplyAI() {
    const btn = document.getElementById('btnSubmit');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Submitting...';

    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab?.id) throw new Error('No active tab');

      const result = await chrome.tabs.sendMessage(tab.id, { action: 'submitToComplyAI' });

      if (result?.success) {
        btn.innerHTML = `Submitted ${result.submitted}`;
        btn.style.background = '#48bb78';
      } else {
        btn.innerHTML = 'Submit failed';
        btn.style.background = '#f56565';
        console.warn('ComplyAI submit failed:', result?.error);
      }
    } catch (error) {
      btn.innerHTML = 'Submit failed';
      btn.style.background = '#f56565';
      console.error('ComplyAI submit failed:', error);
    }

    setTimeout(() => {
      btn.disabled = false;
      btn.style.background = '';
      btn.innerHTML = original;
    }, 2500);
  }

  updateConnectionStatus(connected) {
    const indicator = document.getElementById('statusIndicator');
    const dot = indicator.querySelector('.dot') || document.createElement('span');
    dot.className = 'dot';
    
    if (connected) {
      indicator.className = 'status-indicator connected';
      indicator.innerHTML = '<span class="dot"></span><span>Connected</span>';
    } else {
      indicator.className = 'status-indicator disconnected';
      indicator.innerHTML = '<span class="dot"></span><span>Disconnected</span>';
    }
  }

  async fillAll() {
    const btn = document.getElementById('btnFillAll');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Filling...';

    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tab?.id) {
      try {
        await chrome.tabs.sendMessage(tab.id, { action: 'fillAll' });
        alert('Auto-fill completed!');
      } catch (e) {
        console.error('Fill failed:', e);
        alert('Fill failed. Make sure you are on a supported questionnaire page.');
      }
    }

    btn.disabled = false;
    btn.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path>
      </svg>
      Auto-Fill All
    `;
  }

  showContentScriptError() {
    const stats = document.querySelector('.stat-row');
    if (stats) {
      stats.parentElement.innerHTML = `
        <div style="padding: 16px; text-align: center; color: #a0aec0;">
          Content script not loaded.<br>
          <small>Refresh the page and try again.</small>
        </div>
      `;
    }
    this.updateConnectionStatus(false);
  }

  showNoQuestionsMessage() {
    document.querySelector('.section:last-of-type').innerHTML = `
      <div style="padding: 16px; text-align: center; color: #a0aec0;">
        No questions detected on this page.<br>
        <small>Navigate to a questionnaire page (OneTrust, Whistic, Loopio, etc.)</small>
      </div>
    `;
  }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  new PopupController();
});