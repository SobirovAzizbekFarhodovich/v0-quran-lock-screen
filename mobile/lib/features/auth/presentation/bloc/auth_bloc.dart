import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../services/api_client.dart';
import 'auth_event.dart';
import 'auth_state.dart';

class AuthBloc extends Bloc<AuthEvent, AuthState> {
  final ApiClient _apiClient;

  AuthBloc(this._apiClient) : super(AuthInitial()) {
    on<AuthCheckEvent>(_onAuthCheck);
    on<LoginEvent>(_onLogin);
    on<RegisterEvent>(_onRegister);
    on<UpdateProfileEvent>(_onUpdateProfile);
    on<LogoutEvent>(_onLogout);
  }

  Future<void> _onAuthCheck(AuthCheckEvent event, Emitter<AuthState> emit) async {
    emit(AuthLoading());
    try {
      await _apiClient.restore();
      if (_apiClient.isAuthenticated) {
        final profile = await _apiClient.profile();
        emit(AuthAuthenticated(user: profile));
      } else {
        emit(AuthUnauthenticated());
      }
    } catch (e) {
      emit(AuthUnauthenticated());
    }
  }

  Future<void> _onLogin(LoginEvent event, Emitter<AuthState> emit) async {
    emit(AuthLoading());
    try {
      await _apiClient.login(event.email, event.password);
      final profile = await _apiClient.profile();
      emit(AuthAuthenticated(user: profile));
    } catch (e) {
      emit(AuthError(message: "E-mail yoki parol xato: ${e.toString()}"));
    }
  }

  Future<void> _onRegister(RegisterEvent event, Emitter<AuthState> emit) async {
    emit(AuthLoading());
    try {
      await _apiClient.register(event.name, event.email, event.password, event.language);
      final profile = await _apiClient.profile();
      emit(AuthAuthenticated(user: profile));
    } catch (e) {
      emit(AuthError(message: "Ro'yxatdan o'tishda xatolik: ${e.toString()}"));
    }
  }

  Future<void> _onUpdateProfile(UpdateProfileEvent event, Emitter<AuthState> emit) async {
    final currentState = state;
    if (currentState is AuthAuthenticated) {
      emit(AuthLoading());
      try {
        final updatedUser = await _apiClient.updateProfile(event.name, event.language);
        emit(AuthAuthenticated(user: updatedUser));
      } catch (e) {
        emit(AuthError(message: "Profilni tahrirlashda xatolik: ${e.toString()}"));
        // Revert back to authenticated
        emit(AuthAuthenticated(user: currentState.user));
      }
    }
  }

  Future<void> _onLogout(LogoutEvent event, Emitter<AuthState> emit) async {
    emit(AuthLoading());
    try {
      await _apiClient.logout();
      emit(AuthUnauthenticated());
    } catch (e) {
      emit(AuthError(message: "Tizimdan chiqishda xatolik: ${e.toString()}"));
    }
  }
}
