// ComplyAI Content Script - Detects and auto-fills security questionnaire forms
// Works on OneTrust, Whistic, Loopio, CyberGRX, Google Forms, Typeform

class ComplyAIAutofiller {
  constructor() {
    this.apiBaseUrl = 'http://localhost:8080/api'; // Configurable via storage
    this.isEnabled = true;
    this.detectedQuestions = new Map();
    this.init();
  }

  async init() {
    // Load settings from storage
    const settings = await chrome.storage.sync.get(['apiBaseUrl', 'enabled', 'autoFill']);
    this.apiBaseUrl = settings.apiBaseUrl || 'http://localhost:8080/api';
    this.isEnabled = settings.enabled !== false;
    this.autoFill = settings.autoFill !== false;

    if (this.isEnabled) {
      this.detectQuestions();
      this.injectSidebar();
      this.setupMessageListener();
    }
  }

  detectQuestions() {
    // Detect question elements based on platform
    const platform = this.detectPlatform();
    const selectors = this.getSelectorsForPlatform(platform);

    for (const selector of selectors.question) {
      const elements = document.querySelectorAll(selector);
      elements.forEach(el => this.processQuestionElement(el, platform));
    }
  }

  detectPlatform() {
    const hostname = window.location.hostname;
    if (hostname.includes('onetrust')) return 'onetrust';
    if (hostname.includes('whistic')) return 'whistic';
    if (hostname.includes('loopio')) return 'loopio';
    if (hostname.includes('cybergrx')) return 'cybergrx';
    if (hostname.includes('google.com') && window.location.pathname.includes('/forms/')) return 'google_forms';
    if (hostname.includes('typeform')) return 'typeform';
    return 'generic';
  }

  getSelectorsForPlatform(platform) {
    const selectors = {
      onetrust: {
        question: '.question-text, .question-title, [data-testid="question-text"], .survey-question',
        answer: 'input[type="text"], textarea, select, [role="combobox"]',
        options: '.answer-option, .choice-option, [role="option"]',
        container: '.question-container, .survey-question-row'
      },
      whistic: {
        question: '.question-content, .question-title, [data-cy="question-text"]',
        answer: 'input, textarea, select',
        options: '.option-item, .choice',
        container: '.question-wrapper'
      },
      loopio: {
        question: '.project-question-text, .question-text',
        answer: '.answer-input, textarea, select',
        options: '.option-button, .choice',
        container: '.project-question'
      },
      cybergrx: {
        question: '.question-label, .question-text',
        answer: 'input, textarea, select',
        options: '.radio-option, .checkbox-option',
        container: '.question-row'
      },
      google_forms: {
        question: '.freebirdFormviewerViewItemsItemItemTitle, .exportItemTitle',
        answer: 'input[type="text"], textarea, .quantumWizTextinputPaperinputInput',
        options: '.docssharedWizToggleLabeledLabelWrapper, .exportOption',
        container: '.freebirdFormviewerViewItemsItemItem'
      },
      typeform: {
        question: '.Question_text, .Question_question',
        answer: 'input, textarea',
        options: '.Choice_option',
        container: '.Question_root'
      },
      generic: {
        question: 'label, .question, .question-text, [class*="question"]',
        answer: 'input[type="text"], textarea, select, [contenteditable="true"]',
        options: '[role="option"], .option, .choice',
        container: '[class*="question"], [class*="field"]'
      }
    };
    return selectors[platform] || selectors.generic;
  }

  processQuestionElement(element, platform) {
    const questionText = this.extractQuestionText(element);
    if (!questionText || questionText.length < 10) return;

    // Generate unique ID for this question
    const questionId = this.generateQuestionId(questionText);
    
    // Find associated answer input
    const answerInput = this.findAnswerInput(element, platform);
    if (!answerInput) return;

    // Store for later filling
    this.detectedQuestions.set(questionId, {
      element,
      answerInput,
      questionText,
      platform,
      type: this.detectQuestionType(answerInput),
      options: this.extractOptions(answerInput)
    });

    // Add visual indicator
    this.addVisualIndicator(element, answerInput, questionId);
  }

  extractQuestionText(element) {
    // Try multiple strategies to get question text
    let text = element.textContent || element.innerText || '';
    
    // Clean up
    text = text.replace(/\s+/g, ' ').trim();
    
    // Remove common prefixes
    text = text.replace(/^(Q\d+[\.\s]*)/i, '').trim();
    text = text.replace(/^(Question\s*\d+[\.\s]*)/i, '').trim();
    
    return text;
  }

  findAnswerInput(questionElement, platform) {
    const container = questionElement.closest(this.getSelectorsForPlatform(platform).container) || questionElement;
    
    // Try different input types
    const selectors = [
      'input[type="text"]',
      'textarea',
      'select',
      '[role="combobox"]',
      '[contenteditable="true"]',
      'input[type="radio"]',
      'input[type="checkbox"]',
      '[role="radio"]',
      '[role="checkbox"]'
    ];

    for (const selector of selectors) {
      const inputs = container.querySelectorAll(selector);
      if (inputs.length > 0) return inputs[0];
    }

    // Check for contenteditable
    const editable = container.querySelector('[contenteditable="true"]');
    if (editable) return editable;

    return null;
  }

  detectQuestionType(input) {
    if (!input) return 'free_text';
    
    const tag = input.tagName.toLowerCase();
    const type = input.type;
    const role = input.getAttribute('role');

    if (tag === 'select') return 'single_choice';
    if (type === 'radio' || role === 'radio') return 'single_choice';
    if (type === 'checkbox' || role === 'checkbox') return 'multiple_choice';
    if (tag === 'textarea') return 'free_text';
    if (type === 'text') return 'free_text';
    if (input.isContentEditable) return 'free_text';
    if (role === 'combobox') return 'single_choice';
    
    return 'free_text';
  }

  extractOptions(input) {
    if (input.tagName === 'SELECT') {
      return Array.from(input.options).map(opt => ({
        value: opt.value,
        text: opt.text
      }));
    }
    
    // For radio/checkbox groups
    const name = input.name;
    if (name) {
      const group = document.querySelectorAll(`input[name="${name}"]`);
      return Array.from(group).map(el => ({
        value: el.value,
        text: el.labels?.[0]?.textContent || el.value
      }));
    }
    
    return [];
  }

  async fetchAnswer(questionText, questionType, options = []) {
    try {
      const response = await fetch(`${this.apiBaseUrl}/vanta/reason`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${await this.getAuthToken()}`
        },
        body: JSON.stringify({
          question: questionText,
          type: questionType,
          options: options
        })
      });

      if (!response.ok) throw new Error('API request failed');
      return await response.json();
    } catch (error) {
      console.error('ComplyAI: Failed to fetch answer', error);
      return null;
    }
  }

  async fillAnswer(questionId) {
    const question = this.detectedQuestions.get(questionId);
    if (!question) return false;

    const answer = await this.fetchAnswer(
      question.questionText, 
      question.type, 
      question.options
    );

    if (!answer) return false;

    const value = answer.selectedOption || answer.answer;
    const filled = this.setAnswerValue(question.answerInput, value);

    // Record the outcome so the popup, side panel and submit action can all
    // report real numbers instead of recomputing from the DOM.
    if (filled) {
      question.filled = true;
      question.filledAnswer = value;
      question.confidence = answer.confidenceScore ?? answer.confidence ?? null;
      question.citations = answer.citations || [];
      question.filledAt = new Date().toISOString();
    }

    return filled;
  }

  setAnswerValue(input, value) {
    if (!input || value == null) return false;

    try {
      const tag = input.tagName.toLowerCase();
      const type = input.type;

      if (tag === 'select') {
        // Find matching option
        for (const option of input.options) {
          if (option.value === value || option.text === value || 
              option.text.toLowerCase().includes(value.toLowerCase())) {
            input.value = option.value;
            input.dispatchEvent(new Event('change', { bubbles: true }));
            return true;
          }
        }
      } else if (input.type === 'radio') {
        const group = document.querySelectorAll(`input[name="${input.name}"]`);
        for (const radio of group) {
          if (radio.value === value || radio.labels?.[0]?.textContent?.includes(value)) {
            radio.checked = true;
            radio.dispatchEvent(new Event('change', { bubbles: true }));
            return true;
          }
        }
      } else if (input.type === 'checkbox') {
        const shouldCheck = ['yes', 'true', '1', 'on'].includes(value.toLowerCase());
        if (input.checked !== shouldCheck) {
          input.checked = shouldCheck;
          input.dispatchEvent(new Event('change', { bubbles: true }));
        }
        return true;
      } else if (input.isContentEditable) {
        input.textContent = value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        return true;
      } else {
        input.value = value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      }
    } catch (e) {
      console.error('ComplyAI: Error setting value', e);
    }
    return false;
  }

  addVisualIndicator(questionElement, answerInput, questionId) {
    // Remove existing indicator
    const existing = questionElement.querySelector('.complyai-indicator');
    if (existing) existing.remove();

    const indicator = document.createElement('div');
    indicator.className = 'complyai-indicator';
    indicator.innerHTML = `
      <button class="complyai-fill-btn" data-question-id="${questionId}" title="Fill with ComplyAI">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path>
        </svg>
      </button>
      <span class="complyai-status">Ready</span>
    `;
    indicator.style.cssText = `
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin-left: 12px;
      padding: 4px 8px;
      background: #f0f4f8;
      border-radius: 6px;
      font-size: 12px;
      color: #4a5568;
    `;

    // Add click handler
    const btn = indicator.querySelector('.complyai-fill-btn');
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      this.fillAndUpdate(questionId, btn);
    });

    // Insert after question element
    questionElement.appendChild(indicator);
  }

  async fillAndUpdate(questionId, button) {
    button.disabled = true;
    button.innerHTML = '<span class="spinner"></span>';
    
    const success = await this.fillAnswer(questionId);
    
    const status = button.parentElement.querySelector('.complyai-status');
    if (success) {
      button.innerHTML = '✓';
      button.style.background = '#48bb78';
      status.textContent = 'Filled';
      status.style.color = '#38a169';
    } else {
      button.innerHTML = '✗';
      button.style.background = '#f56565';
      status.textContent = 'Failed';
      status.style.color = '#e53e3e';
    }
    
    setTimeout(() => {
      button.disabled = false;
      button.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path>
        </svg>
      `;
      status.textContent = 'Ready';
      status.style.color = '#4a5568';
    }, 3000);
  }

  injectSidebar() {
    if (document.getElementById('complyai-sidebar')) return;

    const sidebar = document.createElement('div');
    sidebar.id = 'complyai-sidebar';
    sidebar.innerHTML = `
      <div class="complyai-sidebar-header">
        <h3>ComplyAI Auto-Filler</h3>
        <button id="complyai-close-sidebar">×</button>
      </div>
      <div class="complyai-sidebar-content">
        <div class="complyai-stats">
          <span>Questions detected: <strong id="complyai-count">0</strong></span>
        </div>
        <button id="complyai-fill-all" class="complyai-btn primary">Fill All Questions</button>
        <div id="complyai-questions-list"></div>
      </div>
    `;
    sidebar.style.cssText = `
      position: fixed;
      top: 0;
      right: 0;
      width: 320px;
      height: 100vh;
      background: white;
      box-shadow: -4px 0 20px rgba(0,0,0,0.1);
      z-index: 2147483647;
      display: flex;
      flex-direction: column;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      transform: translateX(100%);
      transition: transform 0.3s ease;
    `;

    document.body.appendChild(sidebar);

    // Animate in
    requestAnimationFrame(() => {
      sidebar.style.transform = 'translateX(0)';
    });

    // Event listeners
    document.getElementById('complyai-close-sidebar').addEventListener('click', () => {
      sidebar.style.transform = 'translateX(100%)';
      setTimeout(() => sidebar.remove(), 300);
    });

    document.getElementById('complyai-fill-all').addEventListener('click', () => {
      this.fillAllQuestions();
    });

    this.updateSidebar();
  }

  updateSidebar() {
    const countEl = document.getElementById('complyai-count');
    const listEl = document.getElementById('complyai-questions-list');
    
    if (countEl) countEl.textContent = this.detectedQuestions.size;
    
    if (listEl) {
      listEl.innerHTML = '';
      this.detectedQuestions.forEach((q, id) => {
        const item = document.createElement('div');
        item.className = 'complyai-question-item';
        item.innerHTML = `
          <div class="complyai-q-text">${q.questionText.substring(0, 80)}...</div>
          <div class="complyai-q-meta">
            <span class="complyai-type">${q.type}</span>
            <span class="complyai-platform">${q.platform}</span>
          </div>
          <button class="complyai-fill-one" data-id="${id}">Fill</button>
        `;
        item.style.cssText = `
          padding: 12px;
          border-bottom: 1px solid #edf2f7;
          font-size: 13px;
        `;
        listEl.appendChild(item);
      });

      // Add click handlers
      listEl.querySelectorAll('.complyai-fill-one').forEach(btn => {
        btn.addEventListener('click', (e) => {
          this.fillAndUpdate(e.target.dataset.id, e.target);
        });
      });
    }
  }

  async fillAllQuestions() {
    const questions = Array.from(this.detectedQuestions.entries());
    let filled = 0;

    for (const [id, question] of questions) {
      const btn = document.querySelector(`.complyai-fill-btn[data-question-id="${id}"]`);
      if (btn) {
        await this.fillAndUpdate(id, btn);
        filled++;
        await new Promise(r => setTimeout(r, 200)); // Rate limit
      }
    }

    alert(`ComplyAI: Filled ${filled} questions!`);
  }

  generateQuestionId(text) {
    let hash = 0;
    for (let i = 0; i < text.length; i++) {
      hash = ((hash << 5) - hash) + text.charCodeAt(i);
      hash |= 0;
    }
    return 'q_' + Math.abs(hash).toString(36);
  }

  async getAuthToken() {
    // In production, get from secure storage or OAuth flow
    const result = await chrome.storage.local.get(['authToken']);
    return result.authToken || '';
  }

  /**
   * Plain-object view of the detected questions.
   *
   * The internal Map holds live DOM nodes (`element`, `answerInput`) which the
   * structured-clone algorithm cannot copy, so sending it over
   * chrome.runtime messaging silently produced empty objects.
   */
  serializeQuestions() {
    return Array.from(this.detectedQuestions.entries()).map(([id, q]) => ({
      id,
      questionText: q.questionText,
      platform: q.platform,
      type: q.type,
      options: q.options || [],
      filled: !!q.filled,
      filledAnswer: q.filledAnswer || null,
      confidence: q.confidence ?? null,
      citations: q.citations || [],
      filledAt: q.filledAt || null
    }));
  }

  /**
   * Push everything captured on this portal page back into ComplyAI, so the
   * answers a reviewer approves here land in the answer library rather than
   * living only in the vendor's portal.
   */
  async submitToComplyAI() {
    const questions = this.serializeQuestions();
    const answered = questions.filter(q => q.filled && q.filledAnswer);

    if (answered.length === 0) {
      return { success: false, error: 'Fill at least one answer before submitting.' };
    }

    const token = await this.getAuthToken();
    const payload = {
      platform: this.detectPlatform(),
      sourceUrl: window.location.href,
      pageTitle: document.title,
      capturedAt: new Date().toISOString(),
      answers: answered.map(q => ({
        externalId: q.id,
        questionText: q.questionText,
        questionType: q.type,
        options: q.options,
        answer: q.filledAnswer,
        confidenceScore: q.confidence
      }))
    };

    try {
      const response = await fetch(`${this.apiBaseUrl}/vanta/portal/capture`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        return { success: false, error: `ComplyAI returned HTTP ${response.status}` };
      }

      const data = await response.json();
      return {
        success: true,
        submitted: answered.length,
        questionnaireId: data.questionnaireId || null,
        message: data.message || `Submitted ${answered.length} answers to ComplyAI.`
      };
    } catch (error) {
      return { success: false, error: `Could not reach ComplyAI: ${error.message}` };
    }
  }

  setupMessageListener() {
    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
      if (message.action === 'fillAll') {
        this.fillAllQuestions().then(() => sendResponse({ success: true }));
        return true;
      }
      if (message.action === 'detectQuestions') {
        this.detectQuestions();
        sendResponse({ count: this.detectedQuestions.size });
        return true;
      }
      if (message.action === 'getQuestions') {
        sendResponse({ questions: this.serializeQuestions() });
        return true;
      }
      if (message.action === 'fillQuestion') {
        this.fillAnswer(message.questionId)
          .then(success => sendResponse({ success }))
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
      }
      if (message.action === 'submitToComplyAI') {
        this.submitToComplyAI()
          .then(result => sendResponse(result))
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
      }
    });
  }
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => new ComplyAIAutofiller());
} else {
  new ComplyAIAutofiller();
}

// Also handle dynamic content
const observer = new MutationObserver(() => {
  if (window.complyaiAutofiller) {
    window.complyaiAutofiller.detectQuestions();
  }
});
observer.observe(document.body, { childList: true, subtree: true });

// Export for global access
window.ComplyAIAutofiller = ComplyAIAutofiller;