config.output.publicPath = '/static';
if (config.devServer) {
config.devServer.proxy = [
    {context: ["/api/**.ws"], "target":  "http://127.0.0.1:8080/", secure: false, ws: true },
    {context: ["/state/**"], "target":  "http://127.0.0.1:8080/", secure: false, ws: true, onProxyReqWs: function onProxyReqWs(proxyReq, req, socket, options, head) {
        // let the server know we are going through the webpack proxy
        proxyReq.setHeader('X-Webpack-Dev', '1');
    } },
    {context: ["/"], "target":  "http://127.0.0.1:8080/", secure: false },
    {context: ["/api/**"], "target":  "http://127.0.0.1:8080/", secure: false },
];
}
