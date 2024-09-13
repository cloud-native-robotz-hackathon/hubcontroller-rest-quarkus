"use strict"
var connected = false;
var socket;
const robotSet = new Set();

console.log("Init");

if (!connected) {
    var clientId = generateClientId(6);
    socket = new WebSocket("ws://" + location.host + "/dashboard/" + clientId);
    console.log("Trying to connect [" + clientId + "]");

    socket.onopen = function () {
        connected = true;
        console.log("Connected to the web socket with clientId [" + clientId + "]");
        $("#connect").attr("disabled", true);
        $("#connect").text("Dashboard Connected");
    };
    socket.onmessage = function (m) {
        console.log("Got raw message: " + m.data);
        var robotMessageList = JSON.parse(m.data);
        console.log("JSON: -> " + robotMessageList.length);

        for (var i = 0; i < robotMessageList.length; i++) {
            var robotMessage = robotMessageList[i];

            const robotName = robotMessage.name;
            const robotId = robotName.replace('.', '')

            console.log("robot : " + robotName);
            console.log("command : " + robotMessage.operation);
            console.log("disconnected : " + robotMessage.disconnected);

            if (!robotSet.has(robotName)) {
                robotSet.add(robotName);

                var connectedTitle = "Disconnect";
                if (robotMessage.disconnected === true)
                    connectedTitle = "Connect";

                $("#robotList").append(`<div class="col">` +
                    `<div class="card shadow p-3 mb-5 bg-white rounded" style="width: 18rem;">` +

                    `<div class="card-body">` +
                    `<i class="bi bi-robot card-img-top fs-1"></i>` +
                    `<h6 class="card-title display-4"><div id="robotName">${robotName}</div></h6>` +
                    `</div>` +
                    `<ul class="list-group list-group-light list-group-small">` +
                    ` <li class="list-group-item px-4">Status <i class="bi bi-circle-fill text-danger" id="${robotId}-status" ></i></li>` +
                    ` <li class="list-group-item px-4"># of Operations : <span id="${robotId}-number-operations">0</span>` +
                    `</li>` +
                    `<li class="list-group-item px-4">Last Operation : <span id="${robotId}-last-operation"></span></li>` +
                    ` </ul>` +
                    ` <div class="card-body d-grid gap-3">` +
                    `<button type="submit" class="btn btn-secondary" onclick="disconnect('${robotId}')"><span class="bi bi-plug-fill"></span> <span id="${robotId}-disconnect-text"=>${connectedTitle}</span></button>` +
                    `<button type="submit" class="btn btn-secondary" onclick="runApp('${robotId}')"><span class="bi bi-play"></span> <span>Run Container App</span></button>` +
                    ` </div>` +
                    `</div>` +

                    ` </div>`);
            }
            else if (robotMessage.operation !== null) {
                console.log("Else");
                console.log("ID -> " + "#" + robotId + "-status");

                $("#" + robotId + "-status").removeClass("text-danger");
                $("#" + robotId + "-status").addClass("text-success");
                $("#" + robotId + "-last-operation").text(robotMessage.operation);
                $("#" + robotId + "-number-operations").text(robotMessage.count)
            }


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
    $.ajax({
        url: location.protocol + '//' + location.host + '/robot/disconnect/' + robotId,
        method: 'POST',
        success: function (response) {
            // Handle the API response here
            console.log(response);
            if (response === "true") {
                console.log("Disabling Button -> " + "#" + robotId + "-disconnect-text");
                $("#" + robotId + "-disconnect-text").text("Connect");
            }
            else if (response === "false")
                $("#" + robotId + "-disconnect-text").text("Disconnect");

        },
        error: function (xhr, status, error) {
            // Handle errors here
            console.error(status, error);
        }
    });
}

function runApp(robotId) {
    $.ajax({
        url: location.protocol + '//' + location.host + '/runapp/' + robotId,
        method: 'POST',
        success: function (response) {
            // Handle the API response here
            console.log(response);
        },
        error: function (xhr, status, error) {
            // Handle errors here
            console.error(status, error);
        }
    });
}