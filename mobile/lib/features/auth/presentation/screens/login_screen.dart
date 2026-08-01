import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../../app/colors.dart';
import '../../../../app/text_styles.dart';
import '../../../../app/spacing.dart';
import '../../../../core/di/injector.dart';
import '../../../../services/api_client.dart';
import '../bloc/auth_bloc.dart';
import '../bloc/auth_event.dart';
import '../bloc/auth_state.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _serverIpController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  
  bool _obscurePassword = true;
  bool _showAdvanced = false;
  String _selectedLanguage = 'uz';

  @override
  void initState() {
    super.initState();
    final sharedPrefs = getIt<SharedPreferences>();
    _serverIpController.text = sharedPrefs.getString(ApiClient.serverUrlPreferenceKey) ?? ApiClient.defaultBaseUrl;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    final inputBgColor = isDark ? AppColors.darkSurface2 : AppColors.background;
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;
    final activeBorderColor = isDark ? AppColors.primaryLight : AppColors.primary;

    return Scaffold(
      backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
      body: BlocConsumer<AuthBloc, AuthState>(
        listener: (context, state) {
          if (state is AuthAuthenticated) {
            context.go('/');
          } else if (state is AuthError) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(state.message),
                backgroundColor: AppColors.error,
                behavior: SnackBarBehavior.floating,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            );
          }
        },
        builder: (context, state) {
          return SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.xl),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 400),
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // Header with close button (if can pop) and language selector
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            if (context.canPop())
                              IconButton(
                                icon: const Icon(Icons.close_rounded),
                                onPressed: () => context.pop(),
                                color: isDark ? AppColors.darkText : AppColors.textPrimary,
                              )
                            else
                              const SizedBox(width: 40),
                            
                            // Language drop down
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                              decoration: BoxDecoration(
                                color: isDark ? AppColors.darkSurface : Colors.white,
                                borderRadius: BorderRadius.circular(20),
                                border: Border.all(color: borderColor),
                              ),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: _selectedLanguage,
                                  isDense: true,
                                  icon: const Icon(Icons.arrow_drop_down_rounded, color: AppColors.gold),
                                  style: AppTextStyles.labelMedium.copyWith(
                                    color: isDark ? AppColors.darkText : AppColors.textPrimary,
                                  ),
                                  items: const [
                                    DropdownMenuItem(value: 'uz', child: Text('O\'zbek')),
                                    DropdownMenuItem(value: 'uzk', child: Text('Ўзбек')),
                                    DropdownMenuItem(value: 'ru', child: Text('Русский')),
                                    DropdownMenuItem(value: 'en', child: Text('English')),
                                  ],
                                  onChanged: (val) {
                                    if (val != null) {
                                      setState(() {
                                        _selectedLanguage = val;
                                      });
                                    }
                                  },
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: AppSpacing.x2l),

                        // Logo and Branding
                        Center(
                          child: Column(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(AppSpacing.md),
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: AppColors.primaryLight.withOpacity(0.12),
                                ),
                                child: const Text(
                                  '☪',
                                  style: TextStyle(
                                    fontSize: 48,
                                    color: AppColors.gold,
                                  ),
                                ),
                              ),
                              const SizedBox(height: AppSpacing.md),
                              Text(
                                'Islamic AI',
                                style: AppTextStyles.displayMedium.copyWith(
                                  color: isDark ? AppColors.darkText : AppColors.primary,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: AppSpacing.xs),
                              Text(
                                'Islomiy AI xizmatlariga xush kelibsiz',
                                style: AppTextStyles.bodyMedium.copyWith(
                                  color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: AppSpacing.x3l),

                        // Social Login Section
                        Text(
                          'Social Login',
                          textAlign: TextAlign.center,
                          style: AppTextStyles.labelMedium.copyWith(
                            color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
                            letterSpacing: 1.0,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.md),
                        
                        // Google Login Button
                        Container(
                          height: 50,
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: Colors.grey.shade300),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.04),
                                blurRadius: 8,
                                offset: const Offset(0, 2),
                              )
                            ],
                          ),
                          child: InkWell(
                            onTap: () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Google tizimiga ulanish...')),
                              );
                            },
                            borderRadius: BorderRadius.circular(12),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.g_mobiledata_rounded, size: 36, color: Colors.red),
                                const SizedBox(width: 8),
                                Text(
                                  'Google bilan kirish',
                                  style: AppTextStyles.labelLarge.copyWith(
                                    color: Colors.black87,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        
                        // Apple Login Button (Faqat iOS uchun)
                        if (defaultTargetPlatform == TargetPlatform.iOS) ...[
                          const SizedBox(height: AppSpacing.sm),
                          Container(
                            height: 50,
                            decoration: BoxDecoration(
                              color: Colors.black,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: InkWell(
                              onTap: () {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('Apple ID tizimiga ulanish...')),
                                );
                              },
                              borderRadius: BorderRadius.circular(12),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.apple, color: Colors.white, size: 24),
                                  const SizedBox(width: 8),
                                  Text(
                                    'Apple ID bilan kirish',
                                    style: AppTextStyles.labelLarge.copyWith(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                        
                        const SizedBox(height: AppSpacing.xl),
                        Row(
                          children: [
                            Expanded(child: Divider(color: borderColor)),
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16),
                              child: Text(
                                'yoki',
                                style: AppTextStyles.labelMedium.copyWith(
                                  color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
                                ),
                              ),
                            ),
                            Expanded(child: Divider(color: borderColor)),
                          ],
                        ),
                        const SizedBox(height: AppSpacing.xl),

                        // Form Fields
                        // Email Input
                        TextFormField(
                          controller: _emailController,
                          keyboardType: TextInputType.emailAddress,
                          style: AppTextStyles.bodyLarge.copyWith(
                            color: isDark ? AppColors.darkText : AppColors.textPrimary,
                          ),
                          decoration: InputDecoration(
                            labelText: 'Email',
                            labelStyle: AppTextStyles.labelMedium.copyWith(
                              color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                            ),
                            prefixIcon: Icon(Icons.email_outlined, color: isDark ? AppColors.primaryLight : AppColors.primary),
                            filled: true,
                            fillColor: inputBgColor,
                            contentPadding: const EdgeInsets.symmetric(vertical: 18),
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
                              borderSide: BorderSide(color: activeBorderColor, width: 2),
                            ),
                          ),
                          validator: (v) => v == null || !v.contains('@') ? 'To\'g\'ri email kiriting' : null,
                        ),
                        const SizedBox(height: AppSpacing.md),

                        // Password Input
                        TextFormField(
                          controller: _passwordController,
                          obscureText: _obscurePassword,
                          style: AppTextStyles.bodyLarge.copyWith(
                            color: isDark ? AppColors.darkText : AppColors.textPrimary,
                          ),
                          decoration: InputDecoration(
                            labelText: 'Parol',
                            labelStyle: AppTextStyles.labelMedium.copyWith(
                              color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                            ),
                            prefixIcon: Icon(Icons.lock_outline_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscurePassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                                color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
                              ),
                              onPressed: () {
                                setState(() {
                                  _obscurePassword = !_obscurePassword;
                                });
                              },
                            ),
                            filled: true,
                            fillColor: inputBgColor,
                            contentPadding: const EdgeInsets.symmetric(vertical: 18),
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
                              borderSide: BorderSide(color: activeBorderColor, width: 2),
                            ),
                          ),
                          validator: (v) => v == null || v.length < 8 ? 'Parol kamida 8 belgidan iborat bo\'lishi kerak' : null,
                        ),
                        
                        const SizedBox(height: AppSpacing.sm),
                        
                        // Forgot Password Link
                        Align(
                          alignment: Alignment.centerRight,
                          child: TextButton(
                            onPressed: _requestPasswordReset,
                            child: Text(
                              'Parolni unutdingizmi?',
                              style: AppTextStyles.labelMedium.copyWith(
                                color: isDark ? AppColors.primaryLight : AppColors.primary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: AppSpacing.md),

                        // Login Action Button (Gradient with custom shadow)
                        if (state is AuthLoading)
                          const Center(
                            child: Padding(
                              padding: EdgeInsets.all(8.0),
                              child: CircularProgressIndicator(
                                valueColor: AlwaysStoppedAnimation<Color>(AppColors.primary),
                              ),
                            ),
                          )
                        else
                          Container(
                            height: 52,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(12),
                              gradient: const LinearGradient(
                                colors: [AppColors.primary, AppColors.primaryMid],
                                begin: Alignment.topLeft,
                                end: Alignment.bottomRight,
                              ),
                              boxShadow: [
                                BoxShadow(
                                  color: AppColors.primary.withOpacity(0.35),
                                  blurRadius: 16,
                                  offset: const Offset(0, 6),
                                ),
                              ],
                            ),
                            child: ElevatedButton(
                              onPressed: _submit,
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.transparent,
                                shadowColor: Colors.transparent,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                              ),
                              child: Text(
                                'Kirish',
                                style: AppTextStyles.labelLarge.copyWith(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 16,
                                ),
                              ),
                            ),
                          ),
                        const SizedBox(height: AppSpacing.x2l),

                        // Link to sign up
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              'Hisobingiz yo\'qmi? ',
                              style: AppTextStyles.bodyMedium.copyWith(
                                color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                              ),
                            ),
                            GestureDetector(
                              onTap: () {
                                // Save current IP before routing
                                final ip = _serverIpController.text.trim();
                                if (ip.isNotEmpty) {
                                  final url = ApiClient.normalizeBaseUrl(ip);
                                  getIt<SharedPreferences>().setString(ApiClient.serverUrlPreferenceKey, url);
                                  getIt<ApiClient>().baseUrl = url;
                                }
                                context.push('/register');
                              },
                              child: Text(
                                'Ro\'yxatdan o\'ting',
                                style: AppTextStyles.labelLarge.copyWith(
                                  color: isDark ? AppColors.primaryLight : AppColors.primary,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ),
                        
                        const SizedBox(height: AppSpacing.x2l),
                        
                        // Collapsible Server IP Settings (Clean integration)
                        Theme(
                          data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                          child: ExpansionTile(
                            title: Text(
                              'Qo\'shimcha sozlamalar (Server IP)',
                              style: AppTextStyles.labelMedium.copyWith(
                                color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
                              ),
                            ),
                            trailing: Icon(
                              _showAdvanced ? Icons.keyboard_arrow_up_rounded : Icons.keyboard_arrow_down_rounded,
                              color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
                            ),
                            onExpansionChanged: (val) {
                              setState(() {
                                _showAdvanced = val;
                              });
                            },
                            children: [
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 8.0),
                                child: TextFormField(
                                  controller: _serverIpController,
                                  style: AppTextStyles.bodyMedium.copyWith(
                                    color: isDark ? AppColors.darkText : AppColors.textPrimary,
                                  ),
                                  decoration: InputDecoration(
                                    labelText: 'Server IP',
                                    filled: true,
                                    fillColor: inputBgColor,
                                    border: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(12),
                                      borderSide: BorderSide(color: borderColor),
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        
                        const SizedBox(height: AppSpacing.x2l),
                        
                        // Legal text
                        Text(
                          'Davom etish orqali Foydalanish Shartlari va Maxfiylik Siyosatiga rozilik bildirasiz',
                          textAlign: TextAlign.center,
                          style: AppTextStyles.labelSmall.copyWith(
                            color: isDark ? AppColors.darkText2.withOpacity(0.6) : AppColors.textTertiary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  void _submit() {
    if (_formKey.currentState?.validate() ?? false) {
      final ip = _serverIpController.text.trim();
      final url = ApiClient.normalizeBaseUrl(ip);
      getIt<SharedPreferences>().setString(ApiClient.serverUrlPreferenceKey, url);
      getIt<ApiClient>().baseUrl = url;

      context.read<AuthBloc>().add(
            LoginEvent(
              email: _emailController.text.trim(),
              password: _passwordController.text,
            ),
          );
    }
  }

  Future<void> _requestPasswordReset() async {
    final emailController = TextEditingController(text: _emailController.text.trim());
    final email = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Parolni tiklash'),
        content: TextField(
          controller: emailController,
          keyboardType: TextInputType.emailAddress,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Email'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Bekor qilish')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, emailController.text.trim()), child: const Text('Yuborish')),
        ],
      ),
    );
    emailController.dispose();
    if (email == null || email.isEmpty || !mounted) return;
    try {
      await getIt<ApiClient>().requestPasswordReset(email);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Agar hisob mavjud bo‘lsa, emailga tiklash havolasi yuborildi.')),
      );
    } on ApiException {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('So‘rov yuborilmadi. Internetni tekshirib qayta urinib ko‘ring.')),
      );
    }
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _serverIpController.dispose();
    super.dispose();
  }
}
