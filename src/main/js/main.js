import Vue from 'vue'
import GameApp from '@/GameApp'

Vue.component('game-app', GameApp);

new Vue({
    el: '#app',
    template: "<game-app> </game-app>",
    data: {
        conn: null
    }
});
