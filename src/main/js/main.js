import VueRouter from 'vue-router'
import Vue from 'vue'
import App from '@/GameApp'
import Main from '@/components/Main'
import utils from '@/utils'

Vue.use(VueRouter);
Vue.config.productionTip = false;

function extractPageNumberFromRoute(route) {
    return 1
    // return Number.parseInt(
    //     utils.withDefault(route.query.page, "1")
    // )
}

const router = new VueRouter({
    mode: 'hash',
    routes: [{
        path: "*",
        component: Main,
        props: (route) => ({
            searchText: route.query.q,
            page: extractPageNumberFromRoute(route)
        }),
    }]
})

new Vue({
    // router,
    el: '#app',
    render: h => h(App)
});
