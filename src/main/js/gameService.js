import axios from 'axios'


const gameService = axios.create({
    baseURL: `/`
});

export default {
    sendJoinGameRequest: function () {
        console.log("Sending Http join game request");
        gameService.post("join").then(response => {
            console.log(response.data);
            let webSocketUrl = `ws://localhost:9000${response.data.webSocketUrl}`;
            this.openWebSocketConnection(webSocketUrl)
        })
    },

    openWebSocketConnection: function(url) {
      console.log(`opening websocket connection ${url}`);
      const conn = new WebSocket(url);

      conn.onclose = function (event) {
          console.log(`Connection closed ${event}`)
      };

      conn.onmessage = function (event) {
          console.log(`Message received ${event}`)
      };

      var result = conn.send("test");
      console.log(`${result}`);
      return conn
    }

}