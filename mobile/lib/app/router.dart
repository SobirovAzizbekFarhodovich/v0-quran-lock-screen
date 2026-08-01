import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/presentation/screens/login_screen.dart';
import '../features/auth/presentation/screens/register_screen.dart';
import '../features/auth/presentation/screens/password_reset_screen.dart';
import '../features/auth/presentation/bloc/auth_bloc.dart';
import '../features/auth/presentation/bloc/auth_state.dart';
import '../screens/home_screen.dart';
import '../screens/recitation_screen.dart';
import '../screens/spiritual_screen.dart';
import '../screens/profile_screen.dart';
import '../screens/sessions_screen.dart';
import '../screens/splash_screen.dart';
import '../screens/onboarding_screen.dart';
import '../screens/settings_screen.dart';
import 'colors.dart';
import 'text_styles.dart';

class GoRouterRefreshStream extends ChangeNotifier {
  GoRouterRefreshStream(Stream<dynamic> stream) {
    notifyListeners();
    _subscription = stream.asBroadcastStream().listen((dynamic _) => notifyListeners());
  }

  late final StreamSubscription<dynamic> _subscription;

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }
}

class MainShell extends StatefulWidget {
  final Widget child;
  const MainShell({super.key, required this.child});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _selectedIndex = 0;

  void _onItemTapped(int index, BuildContext context) {
    setState(() {
      _selectedIndex = index;
    });
    switch (index) {
      case 0:
        context.go('/');
        break;
      case 1:
        context.go('/recitation');
        break;
      case 2:
        context.go('/spiritual');
        break;
      case 3:
        context.go('/profile');
        break;
    }
  }

  // Update selected index when route changes (e.g. on direct navigation)
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final location = GoRouterState.of(context).matchedLocation;
    if (location == '/') _selectedIndex = 0;
    if (location == '/recitation') _selectedIndex = 1;
    if (location == '/spiritual') _selectedIndex = 2;
    if (location == '/profile') _selectedIndex = 3;
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Scaffold(
      backgroundColor: AppColors.midnight,
      extendBody: true,
      body: widget.child,
      bottomNavigationBar: Padding(
        padding: EdgeInsets.fromLTRB(20, 0, 20, 12 + bottomPadding),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(28),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
            child: Container(
              height: 68,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(28),
                gradient: LinearGradient(
                  colors: [
                    Colors.white.withOpacity(0.14),
                    Colors.white.withOpacity(0.06),
                  ],
                ),
                border: Border.all(color: Colors.white.withOpacity(0.12)),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.neonTeal.withOpacity(0.12),
                    blurRadius: 24,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _buildNavItem(0, Icons.home_rounded, 'Bosh'),
                  _buildNavItem(1, Icons.mic_rounded, 'Qiroat'),
                  _buildNavItem(2, Icons.favorite_rounded, 'Taskin'),
                  _buildNavItem(3, Icons.person_rounded, 'Profil'),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(int index, IconData icon, String label) {
    final isActive = _selectedIndex == index;
    final color = isActive ? AppColors.neonCyan : AppColors.darkText2;

    return Expanded(
      child: GestureDetector(
        onTap: () => _onItemTapped(index, context),
        behavior: HitTestBehavior.opaque,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 220),
              padding: const EdgeInsets.all(8),
              decoration: isActive
                  ? BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.neonTeal.withOpacity(0.18),
                      boxShadow: [
                        BoxShadow(color: AppColors.neonTeal.withOpacity(0.35), blurRadius: 12),
                      ],
                    )
                  : null,
              child: Icon(icon, color: color, size: 22),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: isActive
                  ? AppTextStyles.labelMedium.copyWith(color: color)
                  : AppTextStyles.labelSmall.copyWith(color: color),
            ),
          ],
        ),
      ),
    );
  }
}

GoRouter createRouter(AuthBloc authBloc) {
  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: GoRouterRefreshStream(authBloc.stream),
    redirect: (context, state) {
      final authState = authBloc.state;
      final isLoggedIn = authState is AuthAuthenticated;
      final location = state.matchedLocation;

      // Allow splash and onboarding to render without auth redirect
      if (location == '/splash' || location == '/onboarding') {
        return null;
      }

      final isLoggingIn = location == '/login' || location == '/register' || location == '/reset-password';

      if (!isLoggedIn) {
        return isLoggingIn ? null : '/login';
      }

      if (isLoggingIn) {
        return '/';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/onboarding',
        builder: (context, state) => const OnboardingScreen(),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: '/reset-password',
        builder: (context, state) => PasswordResetScreen(initialToken: state.uri.queryParameters['token']),
      ),
      ShellRoute(
        builder: (context, state, child) => MainShell(child: child),
        routes: [
          GoRoute(
            path: '/',
            builder: (context, state) => const HomeScreen(),
          ),
          GoRoute(
            path: '/recitation',
            builder: (context, state) => const RecitationScreen(),
          ),
          GoRoute(
            path: '/spiritual',
            builder: (context, state) => const SpiritualScreen(),
          ),
          GoRoute(
            path: '/profile',
            builder: (context, state) => const ProfileScreen(),
          ),
          GoRoute(
            path: '/sessions',
            builder: (context, state) => const SessionsScreen(),
          ),
          GoRoute(
            path: '/settings',
            builder: (context, state) => const SettingsScreen(),
          ),
        ],
      ),
    ],
  );
}
