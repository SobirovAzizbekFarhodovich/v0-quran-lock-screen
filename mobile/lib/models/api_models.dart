class UserProfile {
  const UserProfile({required this.id, required this.name, required this.email, required this.language});

  final String id;
  final String name;
  final String email;
  final String language;

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      email: json['email'] as String? ?? '',
      language: json['language'] as String? ?? 'uz',
    );
  }
}

class AuthResult {
  const AuthResult({required this.accessToken, required this.refreshToken, required this.user});

  final String accessToken;
  final String refreshToken;
  final UserProfile user;

  factory AuthResult.fromJson(Map<String, dynamic> json) {
    return AuthResult(
      accessToken: json['access_token'] as String? ?? '',
      refreshToken: json['refresh_token'] as String? ?? '',
      user: UserProfile.fromJson(json['user'] as Map<String, dynamic>? ?? const {}),
    );
  }
}

class SpiritualAnswer {
  const SpiritualAnswer({required this.answer, required this.sources, required this.cached});

  final String answer;
  final List<Map<String, dynamic>> sources;
  final bool cached;

  factory SpiritualAnswer.fromJson(Map<String, dynamic> json) {
    return SpiritualAnswer(
      answer: json['answer'] as String? ?? '',
      sources: (json['sources'] as List<dynamic>? ?? const []).map((item) => Map<String, dynamic>.from(item as Map)).toList(),
      cached: json['cached'] as bool? ?? false,
    );
  }
}

class RecitationEvent {
  const RecitationEvent({
    required this.jobId,
    this.type,
    this.status,
    this.transcription,
    this.score,
    this.errors = const [],
    this.finalResult = false,
    this.error,
  });

  final String jobId;
  final String? type;
  final String? status;
  final String? transcription;
  final double? score;
  final List<dynamic> errors;
  final bool finalResult;
  final String? error;

  factory RecitationEvent.fromJson(Map<String, dynamic> json) {
    final type = json['type'] as String?;
    final tajweed = json['tajweed'] as Map<String, dynamic>?;
    final directErrors = json['errors'];
    final tajweedErrors = tajweed?['issues'];
    final errors = directErrors is List<dynamic>
        ? directErrors
        : tajweedErrors is List<dynamic>
            ? tajweedErrors
            : const <dynamic>[];
    return RecitationEvent(
      jobId: json['job_id'] as String? ?? json['session_id'] as String? ?? json['stream_id'] as String? ?? '',
      type: type,
      status: json['status'] as String?,
      transcription: json['transcription'] as String? ?? json['transcript'] as String?,
      score: (json['score'] as num?)?.toDouble(),
      errors: errors,
      finalResult: json['final'] as bool? ?? false,
      error: type == 'error' ? (json['error'] as String? ?? json['message'] as String? ?? 'Recitation error') : null,
    );
  }
}
