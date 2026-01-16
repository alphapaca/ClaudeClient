// Proxy API calls to backend server during development
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ['/api'],
        target: 'http://localhost:8080',
        changeOrigin: true
    }
];
