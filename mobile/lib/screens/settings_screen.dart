import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../app/colors.dart';
import '../app/text_styles.dart';
import '../app/spacing.dart';
import '../core/di/injector.dart';
import '../services/api_client.dart';
import '../features/auth/presentation/bloc/auth_bloc.dart';
import '../features/auth/presentation/bloc/auth_event.dart';
import '../features/auth/presentation/bloc/auth_state.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late TextEditingController _serverController;
  String _selectedLanguage = 'uz';

  @override
  void initState() {
    super.initState();
    final apiClient = getIt<ApiClient>();
    _serverController = TextEditingController(text: apiClient.baseUrl);

    final authState = context.read<AuthBloc>().state;
    if (authState is AuthAuthenticated) {
      _selectedLanguage = authState.user.language;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = isDark ? AppColors.darkSurface : Colors.white;
    final textColor = isDark ? AppColors.darkText : AppColors.textPrimary;
    final textSecondary = isDark ? AppColors.darkText2 : AppColors.textSecondary;
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;

    return Scaffold(
      backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
      appBar: AppBar(
        title: const Text('Sozlamalar'),
        backgroundColor: isDark ? AppColors.darkBackground : AppColors.primary,
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.xl),
        children: [
          // --- TIL SOZLAMASI ---
          _buildSectionTitle('Til', isDark),
          const SizedBox(height: AppSpacing.sm),
          Container(
            decoration: _cardDecoration(cardBg, borderColor),
            child: Column(
              children: [
                _buildLanguageOption("O'zbek (Lotin)", 'uz', textColor, borderColor),
                Divider(height: 1, color: borderColor),
                _buildLanguageOption('Ўзбек (Кирил)', 'uzk', textColor, borderColor),
                Divider(height: 1, color: borderColor),
                _buildLanguageOption('Русский', 'ru', textColor, borderColor),
                Divider(height: 1, color: borderColor),
                _buildLanguageOption('English', 'en', textColor, borderColor),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.x2l),

          // --- SERVER SOZLAMASI ---
          _buildSectionTitle('Server', isDark),
          const SizedBox(height: AppSpacing.sm),
          Container(
            decoration: _cardDecoration(cardBg, borderColor),
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Server IP manzili',
                  style: AppTextStyles.labelMedium.copyWith(color: textSecondary),
                ),
                const SizedBox(height: AppSpacing.sm),
                TextField(
                  controller: _serverController,
                  style: AppTextStyles.bodyLarge.copyWith(color: textColor),
                  decoration: InputDecoration(
                    hintText: 'http://192.168.1.100:8080',
                    hintStyle: AppTextStyles.bodyMedium.copyWith(color: textSecondary),
                    filled: true,
                    fillColor: isDark ? AppColors.darkSurface2 : AppColors.background,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(color: borderColor),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(color: borderColor),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: isDark ? AppColors.primaryLight : AppColors.primary,
                        width: 1.5,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: AppSpacing.md),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: _saveServer,
                    child: const Text('Saqlash'),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.x2l),

          // --- ILOVA HAQIDA ---
          _buildSectionTitle('Ilova haqida', isDark),
          const SizedBox(height: AppSpacing.sm),
          Container(
            decoration: _cardDecoration(cardBg, borderColor),
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Versiya', style: AppTextStyles.bodyMedium.copyWith(color: textColor)),
                    Text('1.0.0', style: AppTextStyles.bodyMedium.copyWith(color: textSecondary)),
                  ],
                ),
                const SizedBox(height: AppSpacing.md),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Flutter SDK', style: AppTextStyles.bodyMedium.copyWith(color: textColor)),
                    Text('3.22+', style: AppTextStyles.bodyMedium.copyWith(color: textSecondary)),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.x3l),

          // --- CHIQISH TUGMASI ---
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () {
                context.read<AuthBloc>().add(LogoutEvent());
              },
              icon: const Icon(Icons.logout_rounded, color: AppColors.error),
              label: Text(
                'Tizimdan chiqish',
                style: AppTextStyles.labelLarge.copyWith(color: AppColors.error),
              ),
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AppColors.error),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title, bool isDark) {
    return Text(
      title,
      style: AppTextStyles.headlineMedium.copyWith(
        color: isDark ? AppColors.darkText : AppColors.primary,
        fontWeight: FontWeight.bold,
      ),
    );
  }

  BoxDecoration _cardDecoration(Color bg, Color border) {
    return BoxDecoration(
      color: bg,
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: border),
    );
  }

  Widget _buildLanguageOption(String label, String value, Color textColor, Color borderColor) {
    final isSelected = _selectedLanguage == value;
    return ListTile(
      title: Text(
        label,
        style: AppTextStyles.bodyLarge.copyWith(
          color: textColor,
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        ),
      ),
      trailing: isSelected
          ? const Icon(Icons.check_circle_rounded, color: AppColors.success)
          : Icon(Icons.circle_outlined, color: borderColor),
      onTap: () => _changeLanguage(value),
    );
  }

  void _changeLanguage(String language) {
    setState(() {
      _selectedLanguage = language;
    });
    final authState = context.read<AuthBloc>().state;
    if (authState is AuthAuthenticated) {
      context.read<AuthBloc>().add(
            UpdateProfileEvent(name: authState.user.name, language: language),
          );
    }
  }

  Future<void> _saveServer() async {
    final newUrl = _serverController.text.trim();
    if (newUrl.isEmpty) return;

    final apiClient = getIt<ApiClient>();
    apiClient.baseUrl = newUrl;

    final prefs = getIt<SharedPreferences>();
    await prefs.setString(ApiClient.serverUrlPreferenceKey, ApiClient.normalizeBaseUrl(newUrl));

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Server manzili saqlandi: $newUrl'),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      );
    }
  }

  @override
  void dispose() {
    _serverController.dispose();
    super.dispose();
  }
}
