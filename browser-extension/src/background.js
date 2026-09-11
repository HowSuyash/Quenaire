// Quenaire Background Service Worker

chrome.runtime.onInstalled.addListener((details) => {
  if (details.reason === 'install') {
    // Set default settings
    chrome.storage.sync.set({
      apiBaseUrl: 'http://localhost:8080/api',
      autoFill: true,
      showConfidence: true,
      enabled: true
    });

    // Open welcome page
    chrome.tabs.create({
      url: 'https://quenaire.io/welcome'
    });
  }
});

// Handle side panel opening
chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true });

// Handle messages from content scripts and popup
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  switch (message.action) {
    case 'getQuestions':
      // Forward to content script
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (tabs[0]?.id) {
          chrome.tabs.sendMessage(tabs[0].id, { action: 'getQuestions' }, (response) => {
            sendResponse(response);
          });
        } else {
          sendResponse({ questions: [] });
        }
      });
      return true; // Async response

    case 'fillAll':
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (tabs[0]?.id) {
          chrome.tabs.sendMessage(tabs[0].id, { action: 'fillAll' }, (response) => {
            sendResponse(response);
          });
        } else {
          sendResponse({ success: false, error: 'No active tab' });
        }
      });
      return true;

    case 'fillQuestion':
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (tabs[0]?.id) {
          chrome.tabs.sendMessage(tabs[0].id, { 
            action: 'fillQuestion', 
            questionId: message.questionId 
          }, (response) => {
            sendResponse(response);
          });
        } else {
          sendResponse({ success: false, error: 'No active tab' });
        }
      });
      return true;

    case 'detectQuestions':
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (tabs[0]?.id) {
          chrome.tabs.sendMessage(tabs[0].id, { action: 'detectQuestions' }, (response) => {
            sendResponse(response);
          });
        } else {
          sendResponse({ success: false, error: 'No active tab' });
        }
      });
      return true;

    case 'getAuthToken':
      // Get stored auth token
      chrome.storage.local.get(['authToken'], (result) => {
        sendResponse({ token: result.authToken || null });
      });
      return true;

    case 'setAuthToken':
      chrome.storage.local.set({ authToken: message.token });
      sendResponse({ success: true });
      break;

    case 'openSidePanel':
      chrome.sidePanel.open({ windowId: message.windowId });
      sendResponse({ success: true });
      break;
  }
  return true; // Keep channel open for async responses
});

// Handle tab updates to detect questionnaire pages
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab.url) {
    const questionnairePatterns = [
      /onetrust\.com/,
      /whistic\.com/,
      /loopio\.com/,
      /cybergrx\.com/,
      /google\.com\/forms/,
      /typeform\.com/,
      /questionnaire/,
      /security.*assessment/,
      /vendor.*assessment/
    ];

    const isQuestionnaire = questionnairePatterns.some(pattern => pattern.test(tab.url));
    
    if (isQuestionnaire) {
      // Show page action badge
      chrome.action.setBadgeText({ text: '●', tabId });
      chrome.action.setBadgeBackgroundColor({ color: '#4f46e5', tabId });
      chrome.action.setTitle({ 
        title: 'Quenaire: Questionnaire detected. Click to open.', 
        tabId 
      });
    } else {
      chrome.action.setBadgeText({ text: '', tabId });
    }
  }
});

// Handle storage changes
chrome.storage.onChanged.addListener((changes, namespace) => {
  if (namespace === 'sync' && changes.apiBaseUrl) {
    // Notify all tabs of API URL change
    chrome.tabs.query({}, (tabs) => {
      tabs.forEach(tab => {
        chrome.tabs.sendMessage(tab.id, {
          action: 'configChanged',
          apiBaseUrl: changes.apiBaseUrl.newValue
        }).catch(() => {}); // Ignore errors for tabs without content script
      });
    });
  }
});

// Handle extension icon click
chrome.action.onClicked.addListener((tab) => {
  if (tab.id) {
    chrome.sidePanel.open({ windowId: tab.windowId });
  }
});

// Context menu for right-click fill
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'quenaire-fill',
    title: 'Fill with Quenaire',
    contexts: ['editable', 'selection'],
    documentUrlPatterns: [
      '*://*.onetrust.com/*',
      '*://*.whistic.com/*',
      '*://*.loopio.com/*',
      '*://*.cybergrx.com/*',
      '*://*.google.com/forms/*',
      '*://*.typeform.com/*'
    ]
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === 'quenaire-fill' && tab.id) {
    chrome.tabs.sendMessage(tab.id, { 
      action: 'fillSelection',
      selectionText: info.selectionText
    });
  }
});

// Handle keyboard shortcut
chrome.commands.onCommand.addListener((command) => {
  if (command === 'fill-all') {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.id) {
        chrome.tabs.sendMessage(tabs[0].id, { action: 'fillAll' });
      }
    });
  }
});

// Keep service worker alive
chrome.runtime.setUninstallURL('https://quenaire.io/uninstall');