import 'package:get_it/get_it.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;

import '../../services/api_client.dart';
import '../../core/network/ws_client.dart';

final getIt = GetIt.instance;

Future<void> setupLocator() async {
  // SharedPreferences
  final sharedPrefs = await SharedPreferences.getInstance();
  getIt.registerSingleton<SharedPreferences>(sharedPrefs);

  // Secure Storage
  const secureStorage = FlutterSecureStorage();
  getIt.registerSingleton<FlutterSecureStorage>(secureStorage);

  // Load server IP (default to localhost, or 10.0.2.2 for emulator, but user can change it)
  final savedIp = sharedPrefs.getString('server_ip') ?? 'http://localhost:8080';

  // Dio client
  final dio = Dio(BaseOptions(
    baseUrl: savedIp, // Gateway default
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
  ));
  getIt.registerSingleton<Dio>(dio);

  // ApiClient
  final apiClient = ApiClient(httpClient: http.Client(), storage: secureStorage);
  apiClient.baseUrl = savedIp;
  getIt.registerSingleton<ApiClient>(apiClient);

  // WebSocket Client
  final wsClient = WebSocketClient();
  getIt.registerSingleton<WebSocketClient>(wsClient);
}
