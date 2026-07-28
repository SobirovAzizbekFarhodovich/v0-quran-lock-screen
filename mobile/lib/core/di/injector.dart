import 'package:get_it/get_it.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;

import '../../services/api_client.dart';
import '../../services/recitation_service.dart';
import '../../core/network/ws_client.dart';

final getIt = GetIt.instance;

Future<void> setupLocator() async {
  // SharedPreferences
  final sharedPrefs = await SharedPreferences.getInstance();
  getIt.registerSingleton<SharedPreferences>(sharedPrefs);

  // Secure Storage
  const secureStorage = FlutterSecureStorage();
  getIt.registerSingleton<FlutterSecureStorage>(secureStorage);

  // One canonical setting is used by onboarding/login and Settings.
  final savedUrl = sharedPrefs.getString(ApiClient.serverUrlPreferenceKey) ?? ApiClient.defaultBaseUrl;

  // ApiClient
  final apiClient = ApiClient(httpClient: http.Client(), storage: secureStorage);
  apiClient.baseUrl = savedUrl;
  getIt.registerSingleton<ApiClient>(apiClient);

  // RecitationService
  getIt.registerFactory<RecitationService>(() => RecitationService());

  // WebSocket Client
  final wsClient = WebSocketClient();
  getIt.registerSingleton<WebSocketClient>(wsClient);
}
