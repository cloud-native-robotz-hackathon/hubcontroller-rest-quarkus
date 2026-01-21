"use strict"
var connected = false;
var socket;
const robotSet = new Set();
const stopwatches = {}; // Store stopwatch data for each robot
const cameraIntervals = {}; // Store camera refresh intervals for each robot
const logIntervals = {}; // Store log refresh intervals for each robot
const statusIntervals = {}; // Store status polling intervals for each robot
let cameraEnabled = false; // Global camera toggle state
const CAMERA_REFRESH_INTERVAL = 1000; // Refresh camera every 1 second
const LOG_REFRESH_INTERVAL = 3000; // Refresh logs every 3 seconds
const STATUS_POLL_INTERVAL = 5000; // Poll remote status every 5 seconds

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
        const display = document.getElementById(`${this.robotId}-stopwatch-display`);
        if (display) {
            display.textContent = this.formatTime(this.elapsedTime);
            if (this.running) {
                display.classList.add('running');
            } else {
                display.classList.remove('running');
            }
        }
    }

    updateUI() {
        const startBtn = document.getElementById(`${this.robotId}-stopwatch-start`);
        const stopBtn = document.getElementById(`${this.robotId}-stopwatch-stop`);
        const resetBtn = document.getElementById(`${this.robotId}-stopwatch-reset`);

        if (startBtn && stopBtn && resetBtn) {
            startBtn.disabled = this.running;
            stopBtn.disabled = !this.running;
            resetBtn.disabled = this.running && this.elapsedTime === 0;
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
        stopwatches[robotId].stop();
    }
}

function resetStopwatch(robotId) {
    if (stopwatches[robotId]) {
        stopwatches[robotId].reset();
    }
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
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/remote_status',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 5000,
        success: function(response) {
            // Only set to Online if response is exactly "OK"
            if (response && response.trim() === 'OK') {
                updateRobotStatus(robotId, true);
            } else {
                updateRobotStatus(robotId, false);
            }
        },
        error: function(xhr, status, error) {
            console.error("Status poll error for", robotName, ":", error);
            updateRobotStatus(robotId, false);
        }
    });
}

function updateRobotStatus(robotId, isOnline) {
    const badge = document.getElementById(`${robotId}-status-badge`);
    const statusText = document.getElementById(`${robotId}-status-text`);
    
    if (badge && statusText) {
        if (isOnline) {
            badge.classList.remove('offline');
            badge.classList.add('online');
            statusText.textContent = 'Online';
        } else {
            badge.classList.remove('online');
            badge.classList.add('offline');
            statusText.textContent = 'Offline';
        }
    }
    
    // Also update fullscreen view if this robot is in fullscreen
    if (currentFullscreenRobot === robotId) {
        const fsBadge = document.getElementById(`fs-${robotId}-status-badge`);
        const fsStatusText = document.getElementById(`fs-${robotId}-status-text`);
        
        if (fsBadge && fsStatusText) {
            if (isOnline) {
                fsBadge.classList.remove('offline');
                fsBadge.classList.add('online');
                fsStatusText.textContent = 'Online';
            } else {
                fsBadge.classList.remove('online');
                fsBadge.classList.add('offline');
                fsStatusText.textContent = 'Offline';
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
    
    // Create stopwatch for this robot
    stopwatches[robotId] = new Stopwatch(robotId);
    
    return `
        <div class="robot-card" id="card-${robotId}">
            <div class="card-header">
                <div class="robot-icon">
                    <i class="bi bi-robot"></i>
                </div>
                <div class="card-header-actions">
                    <button class="fullscreen-btn" id="${robotId}-fullscreen-btn" onclick="toggleFullscreen('${robotId}', '${robotName}')" title="Toggle fullscreen">
                        <i class="bi bi-arrows-fullscreen"></i>
                    </button>
                    <div class="robot-status-indicator ${statusClass}" id="${robotId}-status-badge">
                        <i class="bi bi-circle-fill"></i>
                        <span id="${robotId}-status-text">${statusText}</span>
                    </div>
                </div>
            </div>
            <div class="card-body">
                <div class="robot-name">${robotName}</div>
                <div class="camera-section ${cameraVisibleClass}" id="${robotId}-camera-section">
                    <div class="camera-label">
                        <span class="camera-label-text">
                            <i class="bi bi-camera-video"></i>
                            Camera Feed
                        </span>
                        <span class="camera-status loading" id="${robotId}-camera-status">Loading...</span>
                    </div>
                    <div class="camera-view">
                        <img id="${robotId}-camera-img" style="display: none;" alt="Camera feed">
                        <div class="camera-placeholder" id="${robotId}-camera-placeholder">
                            <i class="bi bi-camera-video"></i>
                            Waiting for feed...
                        </div>
                    </div>
                </div>
                <div class="log-section" id="${robotId}-log-section">
                    <div class="log-label">
                        <span class="log-label-text">
                            <i class="bi bi-terminal"></i>
                            Pod Logs
                        </span>
                        <span class="log-status loading" id="${robotId}-log-status">Waiting...</span>
                    </div>
                    <div class="log-view">
                        <pre class="log-content" id="${robotId}-log-content" style="display: none;"></pre>
                        <div class="log-placeholder" id="${robotId}-log-placeholder">
                            <i class="bi bi-terminal"></i>
                            Logs will appear in fullscreen view
                        </div>
                    </div>
                </div>
                <div class="stats-grid">
                    <div class="stat-item">
                        <div class="stat-label">Operations</div>
                        <div class="stat-value" id="${robotId}-number-operations">0</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Last Action</div>
                        <div class="stat-value operation" id="${robotId}-last-operation">—</div>
                    </div>
                </div>
                <div class="stopwatch-section">
                    <div class="stopwatch-label">
                        <i class="bi bi-stopwatch"></i>
                        Stopwatch
                    </div>
                    <div class="stopwatch-display" id="${robotId}-stopwatch-display">00:00.00</div>
                    <div class="stopwatch-controls">
                        <button class="stopwatch-btn start" id="${robotId}-stopwatch-start" onclick="startStopwatch('${robotId}')">
                            <i class="bi bi-play-fill"></i>
                            Start
                        </button>
                        <button class="stopwatch-btn stop" id="${robotId}-stopwatch-stop" onclick="stopStopwatch('${robotId}')" disabled>
                            <i class="bi bi-pause-fill"></i>
                            Stop
                        </button>
                        <button class="stopwatch-btn reset" id="${robotId}-stopwatch-reset" onclick="resetStopwatch('${robotId}')">
                            <i class="bi bi-arrow-counterclockwise"></i>
                            Reset
                        </button>
                    </div>
                </div>
                <div class="card-actions">
                    <button class="btn btn-secondary" onclick="disconnect('${robotId}')">
                        <i class="bi bi-plug-fill"></i>
                        <span id="${robotId}-disconnect-text">${buttonText}</span>
                    </button>
                    <button class="btn btn-primary" onclick="runApp('${robotId}')">
                        <i class="bi bi-play-fill"></i>
                        Run App
                    </button>
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
                }
            }
        } catch (error) {
            console.error("Error processing message:", error);
        }
    };
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

function runApp(robotId) {
    const btn = $(`button[onclick="runApp('${robotId}')"]`);
    const originalHtml = btn.html();
    
    btn.prop('disabled', true);
    btn.html('<i class="bi bi-hourglass-split"></i> Running...');
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/runapp/' + robotId,
        method: 'POST',
        success: function (response) {
            console.log("Run app response:", response);
            btn.html('<i class="bi bi-check-lg"></i> Started!');
            setTimeout(() => {
                btn.html(originalHtml);
            }, 2000);
        },
        error: function (xhr, status, error) {
            console.error("Run app error:", status, error);
            btn.html('<i class="bi bi-x-lg"></i> Error');
            setTimeout(() => {
                btn.html(originalHtml);
            }, 2000);
        },
        complete: function() {
            setTimeout(() => {
                btn.prop('disabled', false);
            }, 2000);
        }
    });
}

// Start the WebSocket connection when the page loads
initWebSocket();
