"use strict"
var connected = false;
var socket;
const robotSet = new Set();
const stopwatches = {}; // Store stopwatch data for each robot
const cameraIntervals = {}; // Store camera refresh intervals for each robot
const logIntervals = {}; // Store log refresh intervals for each robot
const statusIntervals = {}; // Store status polling intervals for each robot
const appRunningState = {}; // Track app running state for each robot
const robotTimes = {}; // Store last recorded times for leaderboard { robotId: { name: string, time: ms } }
let cameraEnabled = false; // Global camera toggle state
const CAMERA_REFRESH_INTERVAL = 1000; // Refresh camera every 1 second
const LOG_REFRESH_INTERVAL = 3000; // Refresh logs every 3 seconds
const STATUS_POLL_INTERVAL = 5000; // Poll remote status every 5 seconds

// HTML escape function for XSS prevention
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

console.log("Initializing Robot Dashboard...");

// Stopwatch class to manage each robot's timer
class Stopwatch {
    constructor(robotId) {
        this.robotId = robotId;
        this.startTime = 0;
        this.elapsedTime = 0;
        this.running = false;
        this.intervalId = null;
    }

    start() {
        if (!this.running) {
            this.startTime = Date.now() - this.elapsedTime;
            this.running = true;
            this.intervalId = setInterval(() => this.update(), 10);
            this.updateUI();
        }
    }

    stop() {
        if (this.running) {
            this.running = false;
            this.elapsedTime = Date.now() - this.startTime;
            if (this.intervalId) {
                clearInterval(this.intervalId);
                this.intervalId = null;
            }
            this.updateUI();
        }
    }

    reset() {
        this.stop();
        this.elapsedTime = 0;
        this.updateDisplay();
        this.updateUI();
    }

    update() {
        this.elapsedTime = Date.now() - this.startTime;
        this.updateDisplay();
    }

    updateDisplay() {
        // Update main display
        const display = document.getElementById(`${this.robotId}-stopwatch-display`);
        if (display) {
            display.textContent = this.formatTime(this.elapsedTime);
            if (this.running) {
                display.classList.add('running');
            } else {
                display.classList.remove('running');
            }
        }
        
        // Also update fullscreen display if it exists
        const fsDisplay = document.getElementById(`fs-${this.robotId}-stopwatch-display`);
        if (fsDisplay) {
            fsDisplay.textContent = this.formatTime(this.elapsedTime);
            if (this.running) {
                fsDisplay.classList.add('running');
            } else {
                fsDisplay.classList.remove('running');
            }
        }
    }

    updateUI() {
        // Update reset button state (only button remaining)
        const resetBtn = document.getElementById(`${this.robotId}-stopwatch-reset`);
        if (resetBtn) {
            resetBtn.disabled = this.running;
        }
        
        // Also update fullscreen reset button if it exists
        const fsResetBtn = document.getElementById(`fs-${this.robotId}-stopwatch-reset`);
        if (fsResetBtn) {
            fsResetBtn.disabled = this.running;
        }
    }

    formatTime(ms) {
        const totalSeconds = Math.floor(ms / 1000);
        const hours = Math.floor(totalSeconds / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;
        const centiseconds = Math.floor((ms % 1000) / 10);

        if (hours > 0) {
            return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
        }
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${centiseconds.toString().padStart(2, '0')}`;
    }
}

// Stopwatch control functions
function startStopwatch(robotId) {
    if (stopwatches[robotId]) {
        stopwatches[robotId].start();
    }
}

function stopStopwatch(robotId) {
    if (stopwatches[robotId]) {
        const stopwatch = stopwatches[robotId];
        stopwatch.stop();
        
        // Record the time for leaderboard if there's a valid time
        if (stopwatch.elapsedTime > 0) {
            // Find the robot name for this robotId
            let robotName = robotId;
            robotSet.forEach(name => {
                if (name.replace(/\./g, '') === robotId) {
                    robotName = name;
                }
            });
            
            robotTimes[robotId] = {
                name: robotName,
                time: stopwatch.elapsedTime
            };
            
            updateLeaderboard();
        }
    }
}

function resetStopwatch(robotId) {
    if (stopwatches[robotId]) {
        stopwatches[robotId].reset();
    }
}

// Leaderboard functions
function updateLeaderboard() {
    const leaderboardBody = document.getElementById('leaderboard-body');
    const emptyState = document.getElementById('leaderboard-empty');
    
    // Convert robotTimes to array and sort by time (lowest first)
    const sortedTimes = Object.entries(robotTimes)
        .map(([robotId, data]) => ({
            robotId,
            name: data.name,
            time: data.time
        }))
        .sort((a, b) => a.time - b.time);
    
    if (sortedTimes.length === 0) {
        if (emptyState) emptyState.style.display = 'block';
        // Remove any existing items
        const existingItems = leaderboardBody.querySelectorAll('.leaderboard-item');
        existingItems.forEach(item => item.remove());
        return;
    }
    
    // Hide empty state
    if (emptyState) emptyState.style.display = 'none';
    
    // Build leaderboard HTML
    let html = '';
    sortedTimes.forEach((entry, index) => {
        const rank = index + 1;
        const formattedTime = formatLeaderboardTime(entry.time);
        
        html += `
            <div class="leaderboard-item" data-robot-id="${entry.robotId}">
                <div class="leaderboard-rank">${rank}</div>
                <div class="leaderboard-info">
                    <div class="leaderboard-name" title="${entry.name}">${entry.name}</div>
                    <div class="leaderboard-time">${formattedTime}</div>
                </div>
            </div>
        `;
    });
    
    // Update the leaderboard (keep empty state element)
    const existingItems = leaderboardBody.querySelectorAll('.leaderboard-item');
    existingItems.forEach(item => item.remove());
    leaderboardBody.insertAdjacentHTML('beforeend', html);
}

function formatLeaderboardTime(ms) {
    const totalSeconds = Math.floor(ms / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    const centiseconds = Math.floor((ms % 1000) / 10);

    if (hours > 0) {
        return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${centiseconds.toString().padStart(2, '0')}`;
}

// Camera functions
function toggleCameraView() {
    cameraEnabled = document.getElementById('camera-toggle').checked;
    console.log("Camera view toggled:", cameraEnabled);

    // Update all robot camera sections
    robotSet.forEach(robotName => {
        const robotId = robotName.replace(/\./g, '');
        const cameraSection = document.getElementById(`${robotId}-camera-section`);
        
        if (cameraSection) {
            if (cameraEnabled) {
                cameraSection.classList.add('visible');
                startCameraStream(robotName, robotId);
            } else {
                cameraSection.classList.remove('visible');
                stopCameraStream(robotId);
            }
        }
    });
}

function startCameraStream(robotName, robotId) {
    // Don't start if already running
    if (cameraIntervals[robotId]) {
        return;
    }

    console.log("Starting camera stream for:", robotName);
    
    // Initial fetch
    fetchCameraImage(robotName, robotId);
    
    // Set up interval for continuous streaming
    cameraIntervals[robotId] = setInterval(() => {
        fetchCameraImage(robotName, robotId);
    }, CAMERA_REFRESH_INTERVAL);
}

function stopCameraStream(robotId) {
    if (cameraIntervals[robotId]) {
        console.log("Stopping camera stream for:", robotId);
        clearInterval(cameraIntervals[robotId]);
        delete cameraIntervals[robotId];
    }
}

function fetchCameraImage(robotName, robotId) {
    const statusEl = document.getElementById(`${robotId}-camera-status`);
    const imgEl = document.getElementById(`${robotId}-camera-img`);
    const placeholderEl = document.getElementById(`${robotId}-camera-placeholder`);

    // Show loading status
    if (statusEl) {
        statusEl.textContent = 'Loading...';
        statusEl.className = 'camera-status loading';
    }

    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/camera',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 5000,
        success: function(response) {
            if (response && response !== 'Robot Not Registered' && response !== 'Robot Disconnected') {
                // Hide placeholder, show image
                if (placeholderEl) placeholderEl.style.display = 'none';
                if (imgEl) {
                    imgEl.style.display = 'block';
                    // The response is base64 encoded image data
                    imgEl.src = 'data:image/png;base64,' + response;
                }
                if (statusEl) {
                    statusEl.textContent = 'Live';
                    statusEl.className = 'camera-status';
                }
            } else {
                showCameraError(robotId, response || 'No data');
            }
        },
        error: function(xhr, status, error) {
            console.error("Camera fetch error for", robotName, ":", error);
            showCameraError(robotId, 'Error');
        }
    });
}

function showCameraError(robotId, message) {
    const statusEl = document.getElementById(`${robotId}-camera-status`);
    const imgEl = document.getElementById(`${robotId}-camera-img`);
    const placeholderEl = document.getElementById(`${robotId}-camera-placeholder`);

    if (statusEl) {
        statusEl.textContent = message;
        statusEl.className = 'camera-status error';
    }
    if (imgEl) imgEl.style.display = 'none';
    if (placeholderEl) {
        placeholderEl.style.display = 'block';
        placeholderEl.innerHTML = `<i class="bi bi-camera-video-off"></i>${message}`;
    }
}

// Log streaming functions
function startLogStream(robotName, robotId, targetElementId) {
    const elementPrefix = targetElementId || robotId;
    
    // Don't start if already running for this element
    const intervalKey = `${elementPrefix}-logs`;
    if (logIntervals[intervalKey]) {
        return;
    }

    console.log("Starting log stream for:", robotName);
    
    // Initial fetch
    fetchPodLogs(robotName, elementPrefix);
    
    // Set up interval for continuous streaming
    logIntervals[intervalKey] = setInterval(() => {
        fetchPodLogs(robotName, elementPrefix);
    }, LOG_REFRESH_INTERVAL);
}

function stopLogStream(robotId, targetElementId) {
    const elementPrefix = targetElementId || robotId;
    const intervalKey = `${elementPrefix}-logs`;
    
    if (logIntervals[intervalKey]) {
        console.log("Stopping log stream for:", robotId);
        clearInterval(logIntervals[intervalKey]);
        delete logIntervals[intervalKey];
    }
}

function fetchPodLogs(robotName, elementPrefix) {
    const statusEl = document.getElementById(`${elementPrefix}-log-status`);
    const contentEl = document.getElementById(`${elementPrefix}-log-content`);
    const placeholderEl = document.getElementById(`${elementPrefix}-log-placeholder`);

    // Show loading status
    if (statusEl) {
        statusEl.textContent = 'Loading...';
        statusEl.className = 'log-status loading';
    }

    $.ajax({
        url: location.protocol + '//' + location.host + '/control/podLogs',
        method: 'GET',
        data: { robot_name: robotName, lines: 100 },
        timeout: 10000,
        success: function(response) {
            if (response && !response.startsWith('Error') && !response.startsWith('No pods')) {
                // Hide placeholder, show logs
                if (placeholderEl) placeholderEl.style.display = 'none';
                if (contentEl) {
                    contentEl.style.display = 'block';
                    contentEl.textContent = response;
                    // Auto-scroll to bottom
                    contentEl.scrollTop = contentEl.scrollHeight;
                }
                if (statusEl) {
                    statusEl.textContent = 'Live';
                    statusEl.className = 'log-status';
                }
            } else {
                showLogError(elementPrefix, response || 'No logs');
            }
        },
        error: function(xhr, status, error) {
            console.error("Log fetch error for", robotName, ":", error);
            showLogError(elementPrefix, xhr.status === 404 ? 'No pod found' : 'Error');
        }
    });
}

function showLogError(elementPrefix, message) {
    const statusEl = document.getElementById(`${elementPrefix}-log-status`);
    const contentEl = document.getElementById(`${elementPrefix}-log-content`);
    const placeholderEl = document.getElementById(`${elementPrefix}-log-placeholder`);

    if (statusEl) {
        statusEl.textContent = message;
        statusEl.className = 'log-status error';
    }
    if (contentEl) contentEl.style.display = 'none';
    if (placeholderEl) {
        placeholderEl.style.display = 'block';
        placeholderEl.innerHTML = `<i class="bi bi-terminal"></i>${message}`;
    }
}

// Robot status polling functions
function startStatusPolling(robotName, robotId) {
    // Don't start if already running
    if (statusIntervals[robotId]) {
        return;
    }

    console.log("Starting status polling for:", robotName);
    
    // Initial fetch
    pollRemoteStatus(robotName, robotId);
    
    // Set up interval for continuous polling
    statusIntervals[robotId] = setInterval(() => {
        pollRemoteStatus(robotName, robotId);
    }, STATUS_POLL_INTERVAL);
}

function stopStatusPolling(robotId) {
    if (statusIntervals[robotId]) {
        console.log("Stopping status polling for:", robotId);
        clearInterval(statusIntervals[robotId]);
        delete statusIntervals[robotId];
    }
}

function pollRemoteStatus(robotName, robotId) {
    const url = location.protocol + '//' + location.host + '/robot/remote_status?user_key=' + encodeURIComponent(robotName);
    console.log("[Status Poll] " + robotName + " -> " + url);
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/remote_status',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 5000,
        success: function(response) {
            // Check if response indicates an error (backend returns 200 with error message)
            const responseStr = response ? response.toString().toLowerCase() : '';
            const isError = responseStr.includes('error') || responseStr.includes('timeout') || responseStr.includes('refused');
            
            if (isError) {
                console.log("[Status Poll] " + robotName + " response: '" + response + "' -> Offline");
                updateRobotStatus(robotId, false);
            } else {
                console.log("[Status Poll] " + robotName + " response: '" + response + "' -> Online");
                updateRobotStatus(robotId, true);
            }
        },
        error: function(xhr, status, error) {
            console.error("[Status Poll] " + robotName + " error: " + status + " - " + error);
            updateRobotStatus(robotId, false);
        }
    });
}

function updateRobotStatus(robotId, isOnline) {
    const badge = document.getElementById(`${robotId}-status-badge`);
    const statusText = document.getElementById(`${robotId}-status-text`);
    const statusIcon = document.getElementById(`${robotId}-status-icon`);
    
    if (badge && statusText) {
        // Once online, we switch from skupper state to online/offline mode
        if (isOnline) {
            badge.setAttribute('data-seen-online', 'true');
        }
        
        const seenOnline = badge.getAttribute('data-seen-online') === 'true';
        
        if (seenOnline) {
            // Switch to online/offline mode
            badge.classList.remove('skupper-state', 'state-initial', 'state-token-request', 'state-cert-created', 'state-cert-retrieved');
            
            if (isOnline) {
                badge.classList.remove('offline');
                badge.classList.add('online');
                statusText.textContent = 'Online';
            } else {
                badge.classList.remove('online');
                badge.classList.add('offline');
                statusText.textContent = 'Offline';
            }
            
            // Update icon to circle
            if (statusIcon) {
                statusIcon.className = 'bi bi-circle-fill';
            }
        }
    }
    
    // Also update fullscreen view if this robot is in fullscreen
    if (currentFullscreenRobot === robotId) {
        const fsBadge = document.getElementById(`fs-${robotId}-status-badge`);
        const fsStatusText = document.getElementById(`fs-${robotId}-status-text`);
        const fsStatusIcon = document.getElementById(`fs-${robotId}-status-icon`);
        
        if (fsBadge && fsStatusText) {
            const seenOnline = fsBadge.getAttribute('data-seen-online') === 'true';
            
            if (isOnline) {
                fsBadge.setAttribute('data-seen-online', 'true');
            }
            
            if (seenOnline || isOnline) {
                fsBadge.classList.remove('skupper-state', 'state-initial', 'state-token-request', 'state-cert-created', 'state-cert-retrieved');
                
                if (isOnline) {
                    fsBadge.classList.remove('offline');
                    fsBadge.classList.add('online');
                    fsStatusText.textContent = 'Online';
                } else {
                    fsBadge.classList.remove('online');
                    fsBadge.classList.add('offline');
                    fsStatusText.textContent = 'Offline';
                }
                
                if (fsStatusIcon) {
                    fsStatusIcon.className = 'bi bi-circle-fill';
                }
            }
        }
    }
}

// Get icon for operation type
function getOperationIcon(operation) {
    if (!operation) return '';
    
    const iconMap = {
        'forward': 'bi-arrow-up-circle-fill',
        'backward': 'bi-arrow-down-circle-fill',
        'left': 'bi-arrow-left-circle-fill',
        'right': 'bi-arrow-right-circle-fill',
        'distance': 'bi-rulers',
        'remote_status': 'bi-wifi',
        'camera': 'bi-camera-video-fill',
        'status': 'bi-check-circle-fill'
    };
    
    const iconClass = iconMap[operation.toLowerCase()] || 'bi-gear-fill';
    return `<i class="bi ${iconClass}"></i> `;
}

// Fullscreen functions
let currentFullscreenRobot = null;
let currentFullscreenRobotName = null;

function toggleFullscreen(robotId, robotName) {
    const overlay = document.getElementById('fullscreen-overlay');
    const card = document.getElementById(`card-${robotId}`);
    const btn = document.getElementById(`${robotId}-fullscreen-btn`);
    
    if (currentFullscreenRobot === robotId) {
        // Close fullscreen
        closeFullscreen();
    } else {
        // Open fullscreen
        if (currentFullscreenRobot) {
            // Close previous fullscreen first - stop streams
            stopFullscreenStreams();
            const prevBtn = document.getElementById(`${currentFullscreenRobot}-fullscreen-btn`);
            if (prevBtn) {
                prevBtn.classList.remove('active');
                prevBtn.innerHTML = '<i class="bi bi-arrows-fullscreen"></i>';
            }
        }
        
        // Clone the card for fullscreen display
        const cardClone = card.cloneNode(true);
        cardClone.id = `fullscreen-card-${robotId}`;
        
        // Update IDs in clone to avoid conflicts with original
        const fsPrefix = `fs-${robotId}`;
        cardClone.querySelectorAll('[id]').forEach(el => {
            el.id = el.id.replace(robotId, fsPrefix);
        });
        
        // Update button in clone to close fullscreen
        const cloneBtn = cardClone.querySelector('.fullscreen-btn');
        if (cloneBtn) {
            cloneBtn.classList.add('active');
            cloneBtn.innerHTML = '<i class="bi bi-fullscreen-exit"></i>';
            cloneBtn.onclick = () => closeFullscreen();
        }
        
        // Clear and add the cloned card to overlay
        overlay.innerHTML = '';
        overlay.appendChild(cardClone);
        
        // Show overlay
        overlay.classList.add('active');
        document.body.style.overflow = 'hidden';
        
        // Update original button
        btn.classList.add('active');
        btn.innerHTML = '<i class="bi bi-fullscreen-exit"></i>';
        
        currentFullscreenRobot = robotId;
        currentFullscreenRobotName = robotName;
        
        // Always show camera in fullscreen (regardless of global toggle)
        const fsCamera = cardClone.querySelector('.camera-section');
        if (fsCamera) {
            fsCamera.classList.add('visible');
        }
        
        // Always show log section in fullscreen
        const fsLog = cardClone.querySelector('.log-section');
        if (fsLog) {
            fsLog.classList.add('visible');
        }
        
        // Start camera stream for fullscreen view
        startFullscreenCameraStream(robotName, fsPrefix);
        
        // Start log stream for fullscreen view
        startLogStream(robotName, robotId, fsPrefix);
    }
}

function startFullscreenCameraStream(robotName, fsPrefix) {
    // Don't start if already running
    const intervalKey = `${fsPrefix}-camera`;
    if (cameraIntervals[intervalKey]) {
        return;
    }

    console.log("Starting fullscreen camera stream for:", robotName);
    
    // Initial fetch
    fetchFullscreenCameraImage(robotName, fsPrefix);
    
    // Set up interval for continuous streaming
    cameraIntervals[intervalKey] = setInterval(() => {
        fetchFullscreenCameraImage(robotName, fsPrefix);
    }, CAMERA_REFRESH_INTERVAL);
}

function fetchFullscreenCameraImage(robotName, fsPrefix) {
    const statusEl = document.getElementById(`${fsPrefix}-camera-status`);
    const imgEl = document.getElementById(`${fsPrefix}-camera-img`);
    const placeholderEl = document.getElementById(`${fsPrefix}-camera-placeholder`);

    if (statusEl) {
        statusEl.textContent = 'Loading...';
        statusEl.className = 'camera-status loading';
    }

    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/camera',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 5000,
        success: function(response) {
            if (response && response !== 'Robot Not Registered' && response !== 'Robot Disconnected') {
                if (placeholderEl) placeholderEl.style.display = 'none';
                if (imgEl) {
                    imgEl.style.display = 'block';
                    imgEl.src = 'data:image/png;base64,' + response;
                }
                if (statusEl) {
                    statusEl.textContent = 'Live';
                    statusEl.className = 'camera-status';
                }
            } else {
                if (statusEl) {
                    statusEl.textContent = response || 'No data';
                    statusEl.className = 'camera-status error';
                }
                if (imgEl) imgEl.style.display = 'none';
                if (placeholderEl) {
                    placeholderEl.style.display = 'block';
                    placeholderEl.innerHTML = `<i class="bi bi-camera-video-off"></i>${response || 'No data'}`;
                }
            }
        },
        error: function(xhr, status, error) {
            console.error("Fullscreen camera fetch error:", error);
            if (statusEl) {
                statusEl.textContent = 'Error';
                statusEl.className = 'camera-status error';
            }
        }
    });
}

function stopFullscreenStreams() {
    if (currentFullscreenRobot) {
        const fsPrefix = `fs-${currentFullscreenRobot}`;
        
        // Stop fullscreen camera stream
        const cameraKey = `${fsPrefix}-camera`;
        if (cameraIntervals[cameraKey]) {
            clearInterval(cameraIntervals[cameraKey]);
            delete cameraIntervals[cameraKey];
        }
        
        // Stop fullscreen log stream
        stopLogStream(currentFullscreenRobot, fsPrefix);
    }
}

function closeFullscreen() {
    const overlay = document.getElementById('fullscreen-overlay');
    
    if (currentFullscreenRobot) {
        // Stop all fullscreen streams
        stopFullscreenStreams();
        
        const btn = document.getElementById(`${currentFullscreenRobot}-fullscreen-btn`);
        if (btn) {
            btn.classList.remove('active');
            btn.innerHTML = '<i class="bi bi-arrows-fullscreen"></i>';
        }
    }
    
    overlay.classList.remove('active');
    document.body.style.overflow = '';
    currentFullscreenRobot = null;
    currentFullscreenRobotName = null;
}

function closeFullscreenOnOverlay(event) {
    // Only close if clicking on the overlay itself, not the card
    if (event.target.id === 'fullscreen-overlay') {
        closeFullscreen();
    }
}

// Close fullscreen on Escape key
document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape' && currentFullscreenRobot) {
        closeFullscreen();
    }
});

// Update connection status UI
function updateConnectionStatus(isConnected, message) {
    const dot = document.getElementById('status-dot');
    const text = document.getElementById('status-text');
    
    if (isConnected) {
        dot.classList.add('connected');
        text.textContent = message || 'Connected';
    } else {
        dot.classList.remove('connected');
        text.textContent = message || 'Disconnected';
    }
}

// Update robot count
function updateRobotCount() {
    const count = robotSet.size;
    document.getElementById('robot-count').textContent = 
        count === 1 ? '1 robot' : `${count} robots`;
}

// Hide empty state when robots are added
function hideEmptyState() {
    const emptyState = document.getElementById('empty-state');
    if (emptyState) {
        emptyState.style.display = 'none';
    }
}

// Create robot card HTML
function createRobotCard(robotName, robotId, robotMessage) {
    const isDisconnected = robotMessage.disconnected === true;
    // Default status is always Offline - will be updated by remote_status polling
    const statusClass = 'offline';
    const statusText = 'Offline';
    const buttonText = isDisconnected ? 'Connect' : 'Disconnect';
    const cameraVisibleClass = cameraEnabled ? 'visible' : '';
    const initStatus = escapeHtml(robotMessage.initStatus || '');
    const skupperState = escapeHtml(robotMessage.skupperState || 'Skupper');
    
    // Escape robot name and ID for safe HTML insertion
    const safeRobotName = escapeHtml(robotName);
    const safeRobotId = escapeHtml(robotId);
    
    return `
        <div class="robot-card compact" id="card-${safeRobotId}">
            <div class="card-header">
                <div class="robot-icon">
                    <i class="bi bi-robot"></i>
                </div>
                <div class="card-header-actions">
                    <div class="status-badges">
                        <div class="robot-status-indicator skupper-state state-initial" id="${safeRobotId}-status-badge" data-skupper-state="${skupperState}" data-seen-online="false">
                            <i class="bi bi-link-45deg" id="${safeRobotId}-status-icon"></i>
                            <span id="${safeRobotId}-status-text">${skupperState}</span>
                        </div>
                    </div>
                </div>
            </div>
            <div class="card-body">
                <div class="robot-name">${safeRobotName}</div>
                <div class="init-status-section" id="${safeRobotId}-init-status-section" style="${initStatus ? '' : 'display: none;'}">
                    <div class="init-status-title">Robot Status</div>
                    <div class="init-status-label">
                        <span class="init-status-text" id="${safeRobotId}-init-status">${initStatus}</span>
                    </div>
                </div>
                <div class="camera-section ${cameraVisibleClass}" id="${safeRobotId}-camera-section">
                    <div class="camera-label">
                        <span class="camera-label-text">
                            <i class="bi bi-camera-video"></i>
                            Camera Feed
                        </span>
                        <span class="camera-status loading" id="${safeRobotId}-camera-status">Loading...</span>
                    </div>
                    <div class="camera-view">
                        <img id="${safeRobotId}-camera-img" style="display: none;" alt="Camera feed">
                        <div class="camera-placeholder" id="${safeRobotId}-camera-placeholder">
                            <i class="bi bi-camera-video"></i>
                            Waiting for feed...
                        </div>
                    </div>
                </div>
                <div class="stats-grid">
                    <div class="stat-item">
                        <div class="stat-label">Operations</div>
                        <div class="stat-value" id="${safeRobotId}-number-operations">0</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Last Action</div>
                        <div class="stat-value operation" id="${safeRobotId}-last-operation">—</div>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// Initialize WebSocket connection
function initWebSocket() {
    const clientId = generateClientId(6);
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(protocol + "//" + location.host + "/dashboard/" + clientId);
    
    console.log("Connecting to WebSocket [" + clientId + "]...");
    updateConnectionStatus(false, 'Connecting...');

    socket.onopen = function () {
        connected = true;
        console.log("WebSocket connected [" + clientId + "]");
        updateConnectionStatus(true, 'Live');
    };

    socket.onclose = function () {
        connected = false;
        console.log("WebSocket disconnected");
        updateConnectionStatus(false, 'Disconnected');
        
        // Attempt to reconnect after 5 seconds
        setTimeout(function() {
            if (!connected) {
                console.log("Attempting to reconnect...");
                initWebSocket();
            }
        }, 5000);
    };

    socket.onerror = function (error) {
        console.error("WebSocket error:", error);
        updateConnectionStatus(false, 'Error');
    };

    socket.onmessage = function (m) {
        console.log("Received message:", m.data);
        
        try {
            var robotMessageList = JSON.parse(m.data);
            console.log("Processing " + robotMessageList.length + " robot(s)");

            for (var i = 0; i < robotMessageList.length; i++) {
                var robotMessage = robotMessageList[i];
                const robotName = robotMessage.name;
                const robotId = robotName.replace(/\./g, '');

                console.log("Robot:", robotName, "Operation:", robotMessage.operation, "Count:", robotMessage.operationCount);

                if (!robotSet.has(robotName)) {
                    // New robot - add to grid
                    robotSet.add(robotName);
                    hideEmptyState();
                    updateRobotCount();
                    
                    const cardHtml = createRobotCard(robotName, robotId, robotMessage);
                    $("#robotList").append(cardHtml);
                    
                    // Update stats if available
                    if (robotMessage.operationCount > 0) {
                        $("#" + robotId + "-number-operations").text(robotMessage.operationCount);
                    }
                    if (robotMessage.operation) {
                        $("#" + robotId + "-last-operation").html(getOperationIcon(robotMessage.operation) + robotMessage.operation);
                    }
                    
                    // Start camera stream if camera is enabled
                    if (cameraEnabled) {
                        startCameraStream(robotName, robotId);
                    }
                    
                    // Start status polling for this robot
                    startStatusPolling(robotName, robotId);
                } else {
                    // Existing robot - update stats
                    if (robotMessage.operation !== null) {
                        // Update operation count
                        $("#" + robotId + "-number-operations").text(robotMessage.operationCount);
                        
                        // Update last operation with animation and icon
                        const lastOpEl = $("#" + robotId + "-last-operation");
                        lastOpEl.html(getOperationIcon(robotMessage.operation) + robotMessage.operation);
                        lastOpEl.css('color', 'var(--accent-primary)');
                        setTimeout(() => {
                            lastOpEl.css('color', 'var(--accent-secondary)');
                        }, 500);
                        
                        // Note: Status is now managed by remote_status polling, not by operations
                    }
                    
                    // Update disconnect button text based on disconnected flag
                    if (robotMessage.disconnected === true) {
                        $("#" + robotId + "-disconnect-text").text('Connect');
                    } else if (robotMessage.disconnected === false) {
                        $("#" + robotId + "-disconnect-text").text('Disconnect');
                    }
                    
                    // Update init status if present
                    updateInitStatus(robotId, robotMessage.initStatus, robotMessage.initStatusVerbose);
                    
                    // Update skupper state if present
                    updateSkupperState(robotId, robotMessage.skupperState);
                }
            }
        } catch (error) {
            console.error("Error processing message:", error);
        }
    };
}

// Update init status display for a robot
function updateInitStatus(robotId, initStatus, initStatusVerbose) {
    const sectionEl = document.getElementById(`${robotId}-init-status-section`);
    const statusEl = document.getElementById(`${robotId}-init-status`);
    
    if (sectionEl && statusEl) {
        if (initStatus && initStatus.trim() !== '') {
            // Use textContent for safe text insertion (prevents XSS)
            statusEl.textContent = initStatus;
            sectionEl.style.display = '';
            
            // Set tooltip with verbose message if provided (title attribute is safe)
            if (initStatusVerbose && initStatusVerbose.trim() !== '') {
                statusEl.title = initStatusVerbose;
                statusEl.style.cursor = 'help';
            } else {
                statusEl.title = '';
                statusEl.style.cursor = '';
            }
            
            // Add animation effect
            sectionEl.classList.add('status-updated');
            setTimeout(() => sectionEl.classList.remove('status-updated'), 500);
        } else {
            sectionEl.style.display = 'none';
        }
    }
}

// Update skupper state display for a robot
function updateSkupperState(robotId, skupperState) {
    const badgeEl = document.getElementById(`${robotId}-status-badge`);
    const textEl = document.getElementById(`${robotId}-status-text`);
    
    if (badgeEl && textEl && skupperState) {
        // Only update skupper state if we haven't seen the robot online yet
        const seenOnline = badgeEl.getAttribute('data-seen-online') === 'true';
        if (seenOnline) {
            // Store the skupper state but don't display it
            badgeEl.setAttribute('data-skupper-state', skupperState);
            return;
        }
        
        const oldState = badgeEl.getAttribute('data-skupper-state');
        if (oldState !== skupperState) {
            badgeEl.setAttribute('data-skupper-state', skupperState);
            textEl.textContent = skupperState;
            
            // Update badge styling based on state
            badgeEl.classList.remove('state-initial', 'state-token-request', 'state-cert-created', 'state-cert-retrieved');
            
            if (skupperState === 'Skupper') {
                badgeEl.classList.add('state-initial');
            } else if (skupperState === 'Token Request') {
                badgeEl.classList.add('state-token-request');
            } else if (skupperState === 'Secret Cert Created') {
                badgeEl.classList.add('state-cert-created');
            } else if (skupperState === 'Cert Retrieved') {
                badgeEl.classList.add('state-cert-retrieved');
            }
            
            // Add animation effect
            badgeEl.classList.add('state-changed');
            setTimeout(() => badgeEl.classList.remove('state-changed'), 500);
        }
    }
}

function generateClientId(length) {
    var result = '';
    var characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    var charactersLength = characters.length;
    for (var i = 0; i < length; i++) {
        result += characters.charAt(Math.floor(Math.random() * charactersLength));
    }
    return result;
}

function disconnect(robotId) {
    const btn = $(`button[onclick="disconnect('${robotId}')"]`);
    btn.prop('disabled', true);
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/disconnect/' + robotId,
        method: 'POST',
        success: function (response) {
            console.log("Disconnect response:", response);
            
            const badge = $("#" + robotId + "-status-badge");
            const icon = $("#" + robotId + "-status-icon");
            
            // Remove skupper state classes since we're now in online/offline mode
            badge.removeClass('skupper-state state-initial state-token-request state-cert-created state-cert-retrieved');
            badge.attr('data-seen-online', 'true');
            icon.attr('class', 'bi bi-circle-fill');
            
            if (response === "true") {
                // Robot is now disconnected
                badge.removeClass('online').addClass('offline');
                $("#" + robotId + "-status-text").text('Disconnected');
                $("#" + robotId + "-disconnect-text").text("Connect");
            } else if (response === "false") {
                // Robot is now connected
                badge.removeClass('offline').addClass('online');
                $("#" + robotId + "-status-text").text('Online');
                $("#" + robotId + "-disconnect-text").text("Disconnect");
            }
        },
        error: function (xhr, status, error) {
            console.error("Disconnect error:", status, error);
        },
        complete: function() {
            btn.prop('disabled', false);
        }
    });
}

function toggleApp(robotId) {
    const isRunning = appRunningState[robotId] || false;
    
    if (isRunning) {
        stopApp(robotId);
    } else {
        runApp(robotId);
    }
}

function runApp(robotId) {
    const btn = $(`button[onclick="toggleApp('${robotId}')"]`);
    
    btn.prop('disabled', true);
    btn.html('<i class="bi bi-hourglass-split"></i> Starting...');
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/runapp/' + robotId,
        method: 'POST',
        success: function (response) {
            console.log("Run app response:", response);
            
            // Update state to running
            appRunningState[robotId] = true;
            
            // Start the stopwatch when app starts successfully
            startStopwatch(robotId);
            
            // Show success briefly, then update to Stop App button
            btn.html('<i class="bi bi-check-lg"></i> Started!');
            setTimeout(() => {
                updateAppButton(robotId, true);
                btn.prop('disabled', false);
            }, 1000);
        },
        error: function (xhr, status, error) {
            console.error("Run app error:", status, error);
            btn.html('<i class="bi bi-x-lg"></i> Error');
            setTimeout(() => {
                updateAppButton(robotId, false);
                btn.prop('disabled', false);
            }, 2000);
        }
    });
}

function stopApp(robotId) {
    const btn = $(`button[onclick="toggleApp('${robotId}')"]`);
    
    btn.prop('disabled', true);
    btn.html('<i class="bi bi-hourglass-split"></i> Stopping...');
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/stopapp/' + robotId,
        method: 'POST',
        success: function (response) {
            console.log("Stop app response:", response);
            
            // Update state to stopped
            appRunningState[robotId] = false;
            
            // Stop the stopwatch when app stops successfully
            stopStopwatch(robotId);
            
            // Show success briefly, then update to Run App button
            btn.html('<i class="bi bi-check-lg"></i> Stopped!');
            setTimeout(() => {
                updateAppButton(robotId, false);
                btn.prop('disabled', false);
            }, 1000);
        },
        error: function (xhr, status, error) {
            console.error("Stop app error:", status, error);
            
            // Still stop the stopwatch and update state even if there's an error
            appRunningState[robotId] = false;
            stopStopwatch(robotId);
            
            btn.html('<i class="bi bi-x-lg"></i> Error');
            setTimeout(() => {
                updateAppButton(robotId, false);
                btn.prop('disabled', false);
            }, 2000);
        }
    });
}

function updateAppButton(robotId, isRunning) {
    // Update main button
    const btn = $(`button[onclick="toggleApp('${robotId}')"]`);
    if (btn.length) {
        if (isRunning) {
            btn.removeClass('btn-primary').addClass('btn-danger');
            btn.html('<i class="bi bi-stop-fill"></i> Stop App');
        } else {
            btn.removeClass('btn-danger').addClass('btn-primary');
            btn.html('<i class="bi bi-play-fill"></i> Run App');
        }
    }
    
    // Update fullscreen button if exists
    const fsBtn = $(`#fullscreen-card-${robotId} button[onclick="toggleApp('${robotId}')"]`);
    if (fsBtn.length) {
        if (isRunning) {
            fsBtn.removeClass('btn-primary').addClass('btn-danger');
            fsBtn.html('<i class="bi bi-stop-fill"></i> Stop App');
        } else {
            fsBtn.removeClass('btn-danger').addClass('btn-primary');
            fsBtn.html('<i class="bi bi-play-fill"></i> Run App');
        }
    }
}

// Fetch and display app info
function fetchAppInfo() {
    $.ajax({
        url: '/control/appInfo',
        method: 'GET',
        dataType: 'json',
        success: function(data) {
            const versionEl = document.getElementById('app-version');
            const buildTimeEl = document.getElementById('app-build-time');
            
            if (versionEl && data.version) {
                versionEl.textContent = 'v' + data.version;
            }
            if (buildTimeEl && data.buildTime) {
                buildTimeEl.textContent = 'Build: ' + data.buildTime;
            }
        },
        error: function(xhr, status, error) {
            console.error("Failed to fetch app info:", error);
        }
    });
}

// Start the WebSocket connection when the page loads
initWebSocket();

// Fetch app info on page load
fetchAppInfo();
