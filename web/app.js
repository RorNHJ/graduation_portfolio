// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

'use strict';

navigator.getUserMedia = navigator.getUserMedia || navigator.mozGetUserMedia || navigator.webkitGetUserMedia;
window.RTCPeerConnection = window.RTCPeerConnection || window.mozRTCPeerConnection || window.webkitRTCPeerConnection;
window.RTCIceCandidate = window.RTCIceCandidate || window.mozRTCIceCandidate || window.webkitRTCIceCandidate;
window.RTCSessionDescription = window.RTCSessionDescription || window.mozRTCSessionDescription || window.webkitRTCSessionDescription;
window.SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition || window.mozSpeechRecognition
    || window.msSpeechRecognition || window.oSpeechRecognition;
const DESKTOP_MEDIA = ['screen', 'window', 'tab', 'audio'];
var sharestream= null;
var pending_request_id = null;
var audioTracks;

var canvas=document.getElementById("myvideo");

var context=canvas.getContext('2d');
var drawing = new Image();
var x = 0;
var y = 0;
var maxw = 1024;
var maxh = 600;
var w = 512;
var h = 300;
var dragok = false;
var video = document.querySelector('video');


// Launch the chooseDesktopMedia().
document.querySelector('#start').addEventListener('click', function(event) {
    pending_request_id = chrome.desktopCapture.chooseDesktopMedia(
        DESKTOP_MEDIA, onAccessApproved);

});
document.querySelector('#streaming').addEventListener('click', function(event) {

    alert("streaming start");



    var webSocket = new WebSocket("ws://10.10.15.28:8080/websocket");
    var messageTextArea = document.getElementById("messageTextArea");
    //웹 소켓이 연결되었을 때 호출되는 이벤트
    webSocket.onopen = function(message){
        messageTextArea.value += "Server connect...\n";
    };
    //웹 소켓이 닫혔을 때 호출되는 이벤트
    webSocket.onclose = function(message){
        messageTextArea.value += "Server Disconnect...\n";
    };
    //웹 소켓이 에러가 났을 때 호출되는 이벤트
    webSocket.onerror = function(message){
        messageTextArea.value += "error...\n";
    };
    //웹 소켓에서 메시지가 날라왔을 때 호출되는 이벤트
    webSocket.onmessage = function(message){
        messageTextArea.value += "Recieve From Server => " + message.data.length +" Byte\n";
        var signal = null;
        try{
            signal = JSON.parse(message.data);
            if (signal.type === 'offer' || signal.type === 'answer' || signal.type === 'candidate') {
                console.log("data:image/png;base64" );
            }else{

            }

        }catch(exception) {
            drawing.src = "data:image/png;base64," + message.data;
        }


    };
    //Send 버튼을 누르면 실행되는 함수

});

// Capture video/audio of media and initialize RTC communication.

var imLocal = false;
var imRemote = false;
var isConnect =false;
var imLocal2 = false;
var imRemote2 = false;
var isConnect2 =false;

var peerConn2 = null;
var localVideoStream = null;
var peerConn = null,
    wsc = new WebSocket("ws://10.10.15.28:8080/signaling"),
    peerConnCfg = {
        'iceServers': [
            {url:'stun:stun.l.google.com:19302'}
        ]
    };
wsc.onopen = function(message){
    messageTextArea.value += "Server connect...\n";
};
//웹 소켓이 닫혔을 때 호출되는 이벤트
wsc.onclose = function(message){
    messageTextArea.value += "Server Disconnect...\n";
};
//웹 소켓이 에러가 났을 때 호출되는 이벤트
wsc.onerror = function(message){
    messageTextArea.value += "error...\n";
};
wsc.onmessage = function (evt) {
    var signal = null;
    signal = JSON.parse(evt.data);

    if (signal.type === 'offer1' && !imLocal) {
        answerCall();
        console.log(" signal.type :   " + signal.type);
        console.log("Received SDP from local peer.:   " + signal.sdp);

        peerConn.setRemoteDescription(new RTCSessionDescription(signal.sdp));
        createAndSendAnswer();
    }


        if (signal.type === 'offer2' && !imLocal2) {
            answerCall2();
            console.log(" signal.type :   " + signal.type);
            console.log("Received SDP from local peer.:   " + signal.sdp);

            peerConn2.setRemoteDescription(new RTCSessionDescription(signal.sdp));
            createAndSendAnswer2();


        }
        if (signal.type === 'answer1' && imLocal) {
            console.log(" signal.type :   " + signal.type);
            console.log("Received SDP from remote peer.:   " + signal.sdp);
            peerConn.setRemoteDescription(new RTCSessionDescription(signal.sdp));

        }
        if (signal.type === 'answer2' && imLocal2) {
            console.log(" signal.type :   " + signal.type);
            console.log("Received SDP from remote peer.:   " + signal.sdp);
            peerConn2.setRemoteDescription(new RTCSessionDescription(signal.sdp));

        }
        if (signal.type === 'candidate1') {
            console.log(" signal.type :   " + signal.type);
            var candidate = new RTCIceCandidate({
                candidate: signal.candidate
            });
            if (imLocal) {
                console.log("Received ICECandidate from remote peer.  " + signal.candidate);

            } else if (imRemote) {
                console.log("Received ICECandidate from Local peer.  " + signal.candidate);

            }
            peerConn.addIceCandidate(new RTCIceCandidate(candidate));

        }
        if (signal.type === 'candidate2') {
            console.log(" signal.type :   " + signal.type);
            var candidate = new RTCIceCandidate({
                candidate: signal.candidate
            });
            if (imLocal2) {
                console.log("Received ICECandidate from remote peer.  " + signal.candidate);

            } else if (imRemote2) {
                console.log("Received ICECandidate from Local peer.  " + signal.candidate);

            }
            peerConn2.addIceCandidate(new RTCIceCandidate(candidate));

        }
        if (signal.closeConnection) {
            console.log("Received 'close call' signal from remote peer.");
            endCall();
        }
    }

var sdpConstraints = {
    'mandatory': {
        'OfferToReceiveAudio': true,
        'OfferToReceiveVideo': true
    }
};
var localStream ;
var videoCallButton = document.getElementById("communication");
var videoCallButton2 = document.getElementById("communication2");

var       endCallButton = document.getElementById("c_stop");
var     localVideo = document.getElementById('localVideo');
var     remoteVideo = document.getElementById('remoteVideo');
var     remoteUser = document.getElementById('remoteUser');
videoCallButton.addEventListener("click", initiateCall);
videoCallButton2.addEventListener("click", initiateCall2);

remoteUser.addEventListener("click", getUserFace);
endCallButton.addEventListener("click", function (evt) {
    wsc.send(JSON.stringify({"closeConnection": true }));
});

function getUserFace() {
    navigator.mediaDevices.getUserMedia({
        audio: true,
        video: true,
        "mandatory": {
            googTypingNoiseDetection: false,
            googEchoCancellation: false,
            //googEchoCancellation2: false,
            googAutoGainControl: false,
            //googAutoGainControl2: false,
            googNoiseSuppression: false,
            //googNoiseSuppression2: false,
            googHighpassFilter: false,
        }
    })
        .then(gotStream2)
        .catch(function(e) {
            alert('getUserMedia() error: ' + e.name);
        });

    function gotStream2(stream) {
        console.log('Adding local stream.');
        localVideo.src = window.URL.createObjectURL(stream);
        localStream = stream;

    }

}

function prepareCall() {
    peerConn = new RTCPeerConnection(peerConnCfg);

    // send any ice candidates to the other peer
    peerConn.onicecandidate = onIceCandidateHandler;
    // once remote stream arrives, show it in the remote video element

    peerConn.onaddstream = onAddStreamHandler;


    peerConn.oniceconnectionstatechange = function(){
        console.log('ICE state: ',peerConn.iceConnectionState);
        if (peerConn.iceConnectionState === "connected"){
            isConnect = true;
        }
    }



};

function prepareCall2() {
    peerConn2 = new RTCPeerConnection(peerConnCfg);
    // send any ice candidates to the other peer
    peerConn2.onicecandidate = onIceCandidateHandler2;
    // once remote stream arrives, show it in the remote video element

    if(imRemote2)
        peerConn2.onaddstream = onAddStreamHandler;
    else
        peerConn2.onaddstream = onAddStreamHandler2;

    peerConn2.oniceconnectionstatechange = function(){
        console.log('ICE state2: ',peerConn2.iceConnectionState);
        if (peerConn2.iceConnectionState === "connected"){
            isConnect2 = true;
        }
    }


    console.log("peerConn.onaddstream");
};



// run start(true) to initiate a call
function initiateCall() {

    imLocal = false;
    navigator.getUserMedia({ "audio": true, "video": false }, function (stream) {
        audioTracks = stream.getAudioTracks()[0];

        // localVideoStream = stream;
        // localVideo.src = URL.createObjectURL(localVideoStream);
    }, function(error) { console.log(error);});
    prepareCall();
    if(sharestream) {
        console.log('sharestream is ture');
        sharestream.addTrack(audioTracks);
        console.log('sharestream.addTrack(audioTracks)');
        peerConn.addStream(sharestream);

    }else {
        console.log('sharestream is fasle');
    }


    imLocal=true;
    createAndSendOffer();
};

// run start(true) to initiate a call
function initiateCall2() {

    imLocal2 = false;
    navigator.getUserMedia({ "audio": true, "video": false }, function (stream) {
        audioTracks = stream.getAudioTracks()[0];

        // localVideoStream = stream;
        // localVideo.src = URL.createObjectURL(localVideoStream);
    }, function(error) { console.log(error);});
    prepareCall2();
    if(sharestream) {
        console.log('sharestream is ture');
        sharestream.addTrack(audioTracks);
        console.log('sharestream.addTrack(audioTracks)');
        peerConn2.addStream(sharestream);

    }else {
        console.log('sharestream is fasle');
    }

    imLocal2=true;
    createAndSendOffer2();

};


function answerCall() {
    console.log("answerCall");
    imRemote = true;
    prepareCall();
    peerConn.addStream(localStream);

    //   peerConn.addStream(localVideoStream);
    // get the local stream, show it in the local video element and send it

};
function answerCall2() {
    console.log("answerCall");
    imRemote2 = true;
    prepareCall2();
    peerConn2.addStream(localStream);

    //   peerConn.addStream(localVideoStream);
    // get the local stream, show it in the local video element and send it

};

function createAndSendOffer() {
    peerConn.createOffer(
        function (offer) {
            if(offer) {
                console.log(".createOffer: "+ offer);
                var off = new RTCSessionDescription(offer);
                peerConn.setLocalDescription(off, function () {
                        wsc.send(JSON.stringify({
                            "type": 'offer1',
                            'sdp': off
                        }));
                        console.log('wsc.send offer: ');
                        imLocal = true;

                    },
                    function (error) {
                        console.log(error);
                    }
                );
            }else{
                console.log(".createOffer error: ");
                this.createAndSendOffer();
            }
        },
        function (error) { console.log(error);},sdpConstraints
    );

};

function createAndSendOffer2() {
    peerConn2.createOffer(
        function (offer) {
            if(offer) {
                console.log(".createOffer: "+ offer);
                var off = new RTCSessionDescription(offer);
                peerConn2.setLocalDescription(off, function () {
                        wsc.send(JSON.stringify({
                            "type": 'offer2',
                            'sdp': off
                        }));
                        console.log('wsc.send offer: ');
                        imLocal2 = true;
                    },
                    function (error) {
                        console.log(error);
                    }
                );
            }else{
                console.log(".createOffer error: ");
                this.createAndSendOffer();
            }
        },
        function (error) { console.log(error);},sdpConstraints
    );
};

function createAndSendAnswer() {
    peerConn.createAnswer(
        function (answer) {
            if(answer){

                console.log(".createanswer: "+ answer);
                var ans = new RTCSessionDescription(answer);
                peerConn.setLocalDescription(ans, function() {
                        wsc.send(JSON.stringify({
                            "type": 'answer1',
                            'sdp' : ans
                        }));
                        console.log('wsc.send answer: ');
                    },
                    function (error) { console.log(error);}
                );
            }else{
                console.log(".createanswer error: ");
                this.createAndSendAnswer();
            }
        },
        function (error) {console.log(error);},sdpConstraints
    );
};
function createAndSendAnswer2() {
    peerConn2.createAnswer(
        function (answer) {
            if(answer){

                console.log(".createanswer: "+ answer);
                var ans = new RTCSessionDescription(answer);
                peerConn2.setLocalDescription(ans, function() {
                        wsc.send(JSON.stringify({
                            "type": 'answer2',
                            'sdp' : ans
                        }));
                        console.log('wsc.send answer: ');
                    },
                    function (error) { console.log(error);}
                );
            }else{
                console.log(".createanswer error: ");
                this.createAndSendAnswer2();
            }
        },
        function (error) {console.log(error);},sdpConstraints
    );
};

function onIceCandidateHandler(evt) {
    if (evt.candidate) {

        if(imRemote && !imLocal){
            wsc.send(JSON.stringify({
                "type": 'Rcandidate1',
                'candidate' : evt.candidate.candidate
            }));
            console.log('Rcandidate1가 생성됨');
        }else {
            wsc.send(JSON.stringify({
                "type": 'candidate1',
                'candidate': evt.candidate.candidate
            }));
            console.log('candidate1가 생성됨');
        }

    } else {
        console.log('End of candidates.');
    }

};
function onIceCandidateHandler2(evt) {
    if (evt.candidate) {
        if(imRemote2 && !imLocal2){
            wsc.send(JSON.stringify({
                "type": 'Rcandidate2',
                'candidate' : evt.candidate.candidate
            }));
            console.log('Rcandidate2가 생성됨');
        }else {

            wsc.send(JSON.stringify({
                "type": 'candidate2',
                'candidate': evt.candidate.candidate
            }));
            console.log('candidate2가 생성됨');
        }
    } else {
        console.log('End of candidates.');
    }

};

function onAddStreamHandler(evt) {
    console.log("onAddStreamHandler");
    videoCallButton.setAttribute("disabled", true);
    endCallButton.removeAttribute("disabled");
    // set remote video stream as source for remote video HTML5 element
    if(evt.stream){
         if(imLocal || imLocal2)
             localVideo.src = window.URL.createObjectURL(evt.stream);
         if(imRemote || imRemote2)
             gotStream(evt.stream);

   }

};
function onAddStreamHandler2(evt) {
    console.log("onAddStreamHandler2");
    videoCallButton.setAttribute("disabled", true);
    endCallButton.removeAttribute("disabled");
    // set remote video stream as source for remote video HTML5 element
    if(evt.stream){
        if(imLocal || imLocal2)
                remoteVideo.src = window.URL.createObjectURL(evt.stream);
        if(imRemote || imRemote2)
            gotStream(evt.stream);
    }

};

function endCall() {

    if (imRemote || imLocal) {
        peerConn.close();
        peerConn = null;
    }
    if (imRemote2 || imLocal2) {
        peerConn2.close();
        peerConn2 = null;
    }
    videoCallButton.removeAttribute("disabled");
    endCallButton.setAttribute("disabled", true);
    if (localVideoStream) {
        localVideoStream.getTracks().forEach(function (track) {
            track.stop();
        });
        // localVideo.src = "";
    }

    if (remoteVideo) remoteVideo.src = "";
    if (localVideo) localVideo.src = "";
};


// Launch webkitGetUserMedia() based on selected media id.
function onAccessApproved(id) {
    if (!id) {
        console.log('Access rejected.');
        return;
    }

    navigator.webkitGetUserMedia({
        audio: {
            mandatory: {
                chromeMediaSource: 'desktop',
                chromeMediaSourceId: id
            }
        },
        video: {
            mandatory: {
                chromeMediaSource: 'desktop',
                chromeMediaSourceId: id,
                maxWidth: screen.width,
                maxHeight: screen.height
            }
        }
    }, gotStream, getUserMediaError);
}

function getUserMediaError(error) {
    console.log('navigator.webkitGetUserMedia() errot: ', error);
}

// Capture video/audio of media and initialize RTC communication.
function gotStream(stream) {
    console.log('Received local stream', stream);


    video.src = URL.createObjectURL(stream);
    sharestream = stream;
    video.play();
    video.addEventListener('play',function () {
        draw(video,context);
    },false)


    stream.onended = function () {
        console.log('Ended');
    };
}
function draw(video,context) {
    context.drawImage(video,0,0,maxw,maxh);
    if(imLocal || imLocal2) {
        context.drawImage(drawing, x - w / 2, y - h / 2, w, h);
        sharestream = canvas.captureStream(25); // 25 FPS
        if (audioTracks) {
            sharestream.addTrack(audioTracks);
        }
    }

    setTimeout(draw,10,video,context);

}
function myMove(e){
    if (dragok){
        x = e.pageX - canvas.offsetLeft;
        y = e.pageY - canvas.offsetTop;
    }
}

function myDown(e){
    if (e.pageX < x + w/2 + canvas.offsetLeft && e.pageX > x - w/2 +
        canvas.offsetLeft && e.pageY < y + h/2 + canvas.offsetTop &&
        e.pageY > y -h/2 + canvas.offsetTop){
        x = e.pageX - canvas.offsetLeft;
        y = e.pageY - canvas.offsetTop;
        dragok = true;
        canvas.onmousemove = myMove;
    }
}

function myUp(){
    dragok = false;
    canvas.onmousemove = null;
}
canvas.onmousedown = myDown;
canvas.onmouseup = myUp;

document.querySelector('#plus').addEventListener('click', function(event) {
    if(w<=maxw) {
        w = w + w / 4;
        h = h + h / 4;
    }
});
document.querySelector('#minus').addEventListener('click', function(event) {
    if(w>=100) {
        w = w - w / 4;
        h = h - h / 4;
    }
});
