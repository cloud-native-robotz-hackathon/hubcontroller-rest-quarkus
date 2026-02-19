"use strict"
var connected = false;
var socket;
const robotSet = new Set();
const cameraIntervals = {};
const statusIntervals = {};
const robotTimes = {};
const robotOnlineState = {};
const CAMERA_REFRESH_INTERVAL = 1000;
const STATUS_POLL_INTERVAL = 5000;
const LOG_REFRESH_INTERVAL = 3000;

let currentFullscreenRobot = null;
let currentFullscreenRobotName = null;
let fullscreenCameraInterval = null;
let fullscreenLogInterval = null;

console.log("Initializing Hackathon Leaderboard...");

function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
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

function updateRobotCount() {
    const count = robotSet.size;
    document.getElementById('robot-count').textContent = 
        count === 1 ? '1 robot' : `${count} robots`;
}

function hideEmptyState() {
    const emptyState = document.getElementById('empty-state');
    if (emptyState) {
        emptyState.style.display = 'none';
    }
}

function createRobotCard(robotName, robotId, robotMessage) {
    const safeRobotName = escapeHtml(robotName);
    const safeRobotId = escapeHtml(robotId);
    
    return `
        <div class="robot-card" id="card-${safeRobotId}" onclick="openFullscreen('${safeRobotId}', '${safeRobotName}')">
            <div class="card-header">
                <div class="robot-icon">
                    <i class="bi bi-robot"></i>
                </div>
                <div class="card-header-actions">
                    <div class="robot-status-indicator offline" id="${safeRobotId}-status-badge">
                        <i class="bi bi-circle-fill"></i>
                        <span id="${safeRobotId}-status-text">Offline</span>
                    </div>
                </div>
            </div>
            <div class="card-body">
                <div class="robot-name">${safeRobotName}</div>
                <div class="camera-section" id="${safeRobotId}-camera-section">
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

function startCameraStream(robotName, robotId) {
    if (cameraIntervals[robotId]) {
        return;
    }

    console.log("Starting camera stream for:", robotName);
    
    fetchCameraImage(robotName, robotId);
    
    cameraIntervals[robotId] = setInterval(() => {
        fetchCameraImage(robotName, robotId);
    }, CAMERA_REFRESH_INTERVAL);
}

function fetchCameraImage(robotName, robotId) {
    const statusEl = document.getElementById(`${robotId}-camera-status`);
    const imgEl = document.getElementById(`${robotId}-camera-img`);
    const placeholderEl = document.getElementById(`${robotId}-camera-placeholder`);
    
    if (!statusEl || !imgEl || !placeholderEl) return;
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/camera',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 5000,
        success: function(response) {
            if (response && response.length > 100 && response.startsWith('data:image')) {
                imgEl.src = response;
                imgEl.style.display = 'block';
                placeholderEl.style.display = 'none';
                statusEl.textContent = 'Live';
                statusEl.className = 'camera-status connected';
            } else {
                imgEl.style.display = 'none';
                placeholderEl.style.display = 'flex';
                statusEl.textContent = 'No feed';
                statusEl.className = 'camera-status error';
            }
        },
        error: function() {
            imgEl.style.display = 'none';
            placeholderEl.style.display = 'flex';
            statusEl.textContent = 'Offline';
            statusEl.className = 'camera-status error';
        }
    });
}

function startStatusPolling(robotName, robotId) {
    if (statusIntervals[robotId]) {
        return;
    }
    
    pollRemoteStatus(robotName, robotId);
    
    statusIntervals[robotId] = setInterval(() => {
        pollRemoteStatus(robotName, robotId);
    }, STATUS_POLL_INTERVAL);
}

function pollRemoteStatus(robotName, robotId) {
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/remote_status',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 5000,
        success: function(response) {
            const responseStr = response ? response.toString().toLowerCase() : '';
            const isError = responseStr.includes('error') || responseStr.includes('timeout') || responseStr.includes('refused');
            
            if (isError) {
                updateRobotStatus(robotId, false);
            } else {
                updateRobotStatus(robotId, true);
            }
        },
        error: function() {
            updateRobotStatus(robotId, false);
        }
    });
}

function updateRobotStatus(robotId, isOnline) {
    robotOnlineState[robotId] = isOnline;
    
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
    
    // Update fullscreen status if this robot is in fullscreen
    if (currentFullscreenRobot === robotId) {
        const fsBadge = document.getElementById('fullscreen-status-badge');
        const fsStatusText = document.getElementById('fullscreen-status-text');
        
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

// Fullscreen functions
function openFullscreen(robotId, robotName) {
    currentFullscreenRobot = robotId;
    currentFullscreenRobotName = robotName;
    
    const overlay = document.getElementById('fullscreen-overlay');
    document.getElementById('fullscreen-robot-name').textContent = robotName;
    
    // Set initial status
    const isOnline = robotOnlineState[robotId] || false;
    const fsBadge = document.getElementById('fullscreen-status-badge');
    const fsStatusText = document.getElementById('fullscreen-status-text');
    
    if (isOnline) {
        fsBadge.classList.remove('offline');
        fsBadge.classList.add('online');
        fsStatusText.textContent = 'Online';
    } else {
        fsBadge.classList.remove('online');
        fsBadge.classList.add('offline');
        fsStatusText.textContent = 'Offline';
    }
    
    // Reset camera placeholder
    document.getElementById('fullscreen-camera-img').style.display = 'none';
    document.getElementById('fullscreen-camera-placeholder').style.display = 'flex';
    document.getElementById('fullscreen-log-content').textContent = 'Connecting to logs...';
    
    overlay.classList.add('active');
    document.body.style.overflow = 'hidden';
    
    // Start fullscreen camera stream
    startFullscreenCameraStream(robotName);
    
    // Start fullscreen log stream
    startFullscreenLogStream(robotName);
}

function closeFullscreen() {
    const overlay = document.getElementById('fullscreen-overlay');
    overlay.classList.remove('active');
    document.body.style.overflow = '';
    
    // Stop fullscreen streams
    if (fullscreenCameraInterval) {
        clearInterval(fullscreenCameraInterval);
        fullscreenCameraInterval = null;
    }
    if (fullscreenLogInterval) {
        clearInterval(fullscreenLogInterval);
        fullscreenLogInterval = null;
    }
    
    currentFullscreenRobot = null;
    currentFullscreenRobotName = null;
}

function startFullscreenCameraStream(robotName) {
    if (fullscreenCameraInterval) {
        clearInterval(fullscreenCameraInterval);
    }
    
    fetchFullscreenCameraImage(robotName);
    
    fullscreenCameraInterval = setInterval(() => {
        fetchFullscreenCameraImage(robotName);
    }, CAMERA_REFRESH_INTERVAL);
}

function fetchFullscreenCameraImage(robotName) {
    const imgEl = document.getElementById('fullscreen-camera-img');
    const placeholderEl = document.getElementById('fullscreen-camera-placeholder');
    
    if (!imgEl || !placeholderEl) return;
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/camera',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 5000,
        success: function(response) {
            if (response && response.length > 100 && response.startsWith('data:image')) {
                imgEl.src = response;
                imgEl.style.display = 'block';
                placeholderEl.style.display = 'none';
            } else {
                imgEl.style.display = 'none';
                placeholderEl.style.display = 'flex';
            }
        },
        error: function() {
            imgEl.style.display = 'none';
            placeholderEl.style.display = 'flex';
        }
    });
}

function startFullscreenLogStream(robotName) {
    if (fullscreenLogInterval) {
        clearInterval(fullscreenLogInterval);
    }
    
    fetchFullscreenLogs(robotName);
    
    fullscreenLogInterval = setInterval(() => {
        fetchFullscreenLogs(robotName);
    }, LOG_REFRESH_INTERVAL);
}

function fetchFullscreenLogs(robotName) {
    const logContent = document.getElementById('fullscreen-log-content');
    if (!logContent) return;
    
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/logs',
        method: 'GET',
        data: { user_key: robotName },
        timeout: 10000,
        success: function(response) {
            if (response) {
                logContent.textContent = response;
                logContent.scrollTop = logContent.scrollHeight;
            } else {
                logContent.textContent = 'No logs available';
            }
        },
        error: function() {
            logContent.textContent = 'Unable to fetch logs';
        }
    });
}

// Close fullscreen on overlay click (but not on card click)
document.addEventListener('click', function(e) {
    const overlay = document.getElementById('fullscreen-overlay');
    if (e.target === overlay) {
        closeFullscreen();
    }
});

// Close fullscreen on Escape key
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && currentFullscreenRobot) {
        closeFullscreen();
    }
});

function formatTime(ms) {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    const centiseconds = Math.floor((ms % 1000) / 10);
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(centiseconds).padStart(2, '0')}`;
}

function removeLeaderboardEntry(robotId) {
    event.stopPropagation();
    delete robotTimes[robotId];
    updateLeaderboard();
}

function updateLeaderboard() {
    const leaderboardBody = document.getElementById('leaderboard-body');
    const leaderboardEmpty = document.getElementById('leaderboard-empty');
    
    const sortedTimes = Object.entries(robotTimes)
        .filter(([id, entry]) => entry.time > 0)
        .sort((a, b) => a[1].time - b[1].time);
    
    if (sortedTimes.length === 0) {
        if (leaderboardEmpty) {
            leaderboardEmpty.style.display = 'block';
        }
        leaderboardBody.querySelectorAll('.leaderboard-item').forEach(el => el.remove());
        return;
    }
    
    if (leaderboardEmpty) {
        leaderboardEmpty.style.display = 'none';
    }
    
    leaderboardBody.querySelectorAll('.leaderboard-item').forEach(el => el.remove());
    
    sortedTimes.forEach(([robotId, entry], index) => {
        const item = document.createElement('div');
        item.className = 'leaderboard-item';
        item.innerHTML = `
            <div class="leaderboard-rank">${index + 1}</div>
            <div class="leaderboard-info">
                <div class="leaderboard-name">${escapeHtml(entry.name)}</div>
            </div>
            <div class="leaderboard-time">${formatTime(entry.time)}</div>
            <button class="leaderboard-remove" onclick="removeLeaderboardEntry('${escapeHtml(robotId)}')" title="Remove entry">
                <i class="bi bi-x"></i>
            </button>
        `;
        leaderboardBody.appendChild(item);
    });
}

function initWebSocket() {
    const clientId = generateClientId(6);
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(protocol + "//" + location.host + "/dashboard/" + clientId);
    
    socket.onopen = function() {
        connected = true;
        updateConnectionStatus(true, 'Live');
        console.log("WebSocket connected");
    };
    
    socket.onclose = function() {
        connected = false;
        updateConnectionStatus(false, 'Reconnecting...');
        console.log("WebSocket disconnected, reconnecting in 3s...");
        setTimeout(initWebSocket, 3000);
    };
    
    socket.onerror = function(error) {
        console.error("WebSocket error:", error);
        updateConnectionStatus(false, 'Error');
    };
    
    socket.onmessage = function(event) {
        try {
            const message = JSON.parse(event.data);
            
            if (Array.isArray(message)) {
                message.forEach(robotMessage => {
                    const robotName = robotMessage.name;
                    if (!robotName) return;
                    
                    const robotId = robotName.replace(/[^a-zA-Z0-9]/g, '-');
                    
                    if (!robotSet.has(robotId)) {
                        robotSet.add(robotId);
                        hideEmptyState();
                        updateRobotCount();
                        
                        const cardHtml = createRobotCard(robotName, robotId, robotMessage);
                        document.getElementById('robotList').insertAdjacentHTML('beforeend', cardHtml);
                        
                        startCameraStream(robotName, robotId);
                        startStatusPolling(robotName, robotId);
                        
                        if (!robotTimes[robotId]) {
                            robotTimes[robotId] = { name: robotName, time: 0 };
                        }
                    }
                    
                    const operation = robotMessage.operation;
                    if (operation && operation !== 'camera' && operation !== 'remote_status') {
                        const operationEl = document.getElementById(`${robotId}-last-operation`);
                        if (operationEl) {
                            operationEl.innerHTML = getOperationIcon(operation) + escapeHtml(operation);
                        }
                    }
                    
                    const operationCount = robotMessage.operationCount || 0;
                    const countEl = document.getElementById(`${robotId}-number-operations`);
                    if (countEl) {
                        countEl.textContent = operationCount;
                    }
                });
            }
        } catch (error) {
            console.error("Error processing message:", error);
        }
    };
}

$(document).ready(function() {
    console.log("Hackathon Leaderboard ready");
    initWebSocket();
});
