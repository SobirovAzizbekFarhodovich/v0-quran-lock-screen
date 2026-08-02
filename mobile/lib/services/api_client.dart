import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

import '../models/api_models.dart';

class ApiClient extends ChangeNotifier {
  ApiClient({http.Client? httpClient, FlutterSecureStorage? storage})
      : _http = httpClient ?? http.Client(),
        _storage = storage ?? const FlutterSecureStorage();

  static const String defaultBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://18.199.100.4');
  static const String serverUrlPreferenceKey = 'server_url';

  final http.Client _http;
  final FlutterSecureStorage _storage;
  String _baseUrl = defaultBaseUrl;
  String get baseUrl => _baseUrl;
  set baseUrl(String value) => _baseUrl = normalizeBaseUrl(value);

  static String normalizeBaseUrl(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return defaultBaseUrl;
    final withScheme = trimmed.contains('://') ? trimmed : 'http://$trimmed';
    return withScheme.replaceFirst(RegExp(r'/+$'), '');
  }
  String? accessToken;
  String? _refreshToken;
  UserProfile? user;

  bool get isAuthenticated => accessToken != null && accessToken!.isNotEmpty;

  Future<void> restore() async {
    accessToken = await _storage.read(key: 'access_token');
    _refreshToken = await _storage.read(key: 'refresh_token');
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    final result = await _postAuth('/api/v1/auth/login', {'email': email, 'password': password});
    await _persist(result);
  }

  Future<void> register(String name, String email, String password, String language) async {
    final result = await _postAuth('/api/v1/auth/register', {'name': name, 'email': email, 'password': password, 'language': language});
    await _persist(result);
  }

  Future<void> requestPasswordReset(String email) async {
    await _send(
      'POST',
      '/api/v1/auth/password-reset/request',
      body: {'email': email},
      authenticated: false,
    );
  }

  Future<void> confirmPasswordReset(String token, String password) async {
    await _send(
      'POST',
      '/api/v1/auth/password-reset/confirm',
      body: {'token': token, 'password': password},
      authenticated: false,
    );
  }

  Future<UserProfile> profile() async {
    final response = await _send('GET', '/api/v1/profile');
    user = UserProfile.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    notifyListeners();
    return user!;
  }

  Future<UserProfile> updateProfile(String name, String language) async {
    final response = await _send('PUT', '/api/v1/profile', body: {'name': name, 'language': language});
    user = UserProfile.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    notifyListeners();
    return user!;
  }

  Future<SpiritualAnswer> spiritualQuery(String message, String language) async {
    final response = await _send('POST', '/api/v1/spiritual/query', body: {'message': message, 'language': language});
    return SpiritualAnswer.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<List<dynamic>> sessions() async {
    final response = await _send('GET', '/api/v1/sessions');
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return json['sessions'] as List<dynamic>? ?? const [];
  }

  Future<void> logout() async {
    try {
      await _send('POST', '/api/v1/auth/logout');
    } catch (_) {
      // Ignore server error on logout — clear local state anyway
    }
    accessToken = null;
    _refreshToken = null;
    user = null;
    await _storage.deleteAll();
    notifyListeners();
  }

  // --- Token Refresh ---

  Future<bool> _tryRefreshToken() async {
    if (_refreshToken == null || _refreshToken!.isEmpty) return false;
    try {
      final uri = Uri.parse('$baseUrl/api/v1/auth/refresh');
      final response = await _http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refresh_token': _refreshToken}),
      );
      if (response.statusCode >= 200 && response.statusCode < 300) {
        final result = AuthResult.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
        await _persist(result);
        return true;
      }
    } catch (_) {}
    return false;
  }

  // --- Internal helpers ---

  Future<AuthResult> _postAuth(String path, Map<String, dynamic> body) async {
    final response = await _send('POST', path, body: body, authenticated: false);
    return AuthResult.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<void> _persist(AuthResult result) async {
    accessToken = result.accessToken;
    _refreshToken = result.refreshToken;
    user = result.user;
    await _storage.write(key: 'access_token', value: result.accessToken);
    await _storage.write(key: 'refresh_token', value: result.refreshToken);
    notifyListeners();
  }

  Future<http.Response> _send(String method, String path, {Map<String, dynamic>? body, bool authenticated = true}) async {
    var response = await _rawSend(method, path, body: body, authenticated: authenticated);

    // Auto-refresh on 401
    if (response.statusCode == 401 && authenticated) {
      final refreshed = await _tryRefreshToken();
      if (refreshed) {
        response = await _rawSend(method, path, body: body, authenticated: true);
      }
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(response.statusCode, response.body);
    }
    return response;
  }

  Future<http.Response> _rawSend(String method, String path, {Map<String, dynamic>? body, bool authenticated = true}) async {
    final uri = Uri.parse('$baseUrl$path');
    final headers = <String, String>{'Content-Type': 'application/json'};
    if (authenticated && accessToken != null) {
      headers['Authorization'] = 'Bearer $accessToken';
    }
    return switch (method) {
      'GET' => await _http.get(uri, headers: headers),
      'POST' => await _http.post(uri, headers: headers, body: jsonEncode(body ?? const {})),
      'PUT' => await _http.put(uri, headers: headers, body: jsonEncode(body ?? const {})),
      'DELETE' => await _http.delete(uri, headers: headers),
      _ => throw UnsupportedError(method),
    };
  }
}

class ApiException implements Exception {
  const ApiException(this.statusCode, this.body);
  final int statusCode;
  final String body;

  @override
  String toString() => 'ApiException($statusCode): $body';
}
