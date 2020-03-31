import axios from 'axios'


const gameService = axios.create({
    baseURL: `/`
});

var conn = null;

export default {
    sendJoinGameRequest: function () {
        console.log("Sending Http join game request");
        gameService.post("join").then(response => {
            console.log("sendJoinGameRequest");
            console.log(response.data);
            let webSocketUrl = `ws://localhost:9000/ws`;
            this.openWebSocketConnection(webSocketUrl)
        })
    },

    openWebSocketConnection: function(url) {
      console.log(`opening websocket connection ${url}`);
      conn = new WebSocket(url);

      conn.onclose = function (event) {
          console.log(`Connection closed ${event.code} ${event.reason}`)
      };

      conn.onmessage = function (event) {
          console.log(`Message received ${JSON.stringify(event.data)}`)
      };
    },

    sendEcho: function(text) {
        if(conn == null) return;
        if(conn.readyState !== 1) {
            console.log(`Connection is not open ${conn.readyState}`);
            return;
        }
        conn.send(text);
    }

}