config.output.publicPath = '/static';
if (config.devServer) {
    config.devServer.proxy['/state'].onProxyReqWs = function onProxyReqWs(proxyReq, req, socket, options, head) {
        // let the server know we are going through the webpack proxy
        proxyReq.setHeader('X-Webpack-Dev', '1');
    }
}