import 'dart:async';
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
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final activeColor = isDark ? AppColors.primaryLight : AppColors.primary;
    final inactiveColor = isDark ? AppColors.textTertiary : const Color(0xFF8A9E8A);
    final backgroundColor = isDark ? AppColors.darkBackground : Colors.white;
    final borderColor = isDark ? AppColors.darkBorder : Colors.grey.shade200;

    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return Scaffold(
      body: widget.child,
      bottomNavigationBar: Container(
        height: 64 + bottomPadding,
        decoration: BoxDecoration(
          color: backgroundColor,
          border: Border(
            top: BorderSide(color: borderColor, width: 0.5),
          ),
        ),
        padding: EdgeInsets.only(bottom: bottomPadding),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _buildNavItem(0, Icons.home_rounded, 'Bosh sahifa', activeColor, inactiveColor),
            _buildNavItem(1, Icons.mic_rounded, 'Qiroat', activeColor, inactiveColor),
            _buildNavItem(2, Icons.favorite_rounded, 'Ruhiy taskin', activeColor, inactiveColor),
            _buildNavItem(3, Icons.person_rounded, 'Profil', activeColor, inactiveColor),
          ],
        ),
      ),
    );
  }

  Widget _buildNavItem(int index, IconData icon, String label, Color activeColor, Color inactiveColor) {
    final isActive = _selectedIndex == index;
    final color = isActive ? activeColor : inactiveColor;

    return Expanded(
      child: GestureDetector(
        onTap: () => _onItemTapped(index, context),
        behavior: HitTestBehavior.opaque,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            // Active top pill indicator
            AnimatedContainer(
              duration: const Duration(milliseconds: 250),
              width: isActive ? 36 : 0,
              height: 4,
              decoration: BoxDecoration(
                color: activeColor,
                borderRadius: const BorderRadius.only(
                  bottomLeft: Radius.circular(2),
                  bottomRight: Radius.circular(2),
                ),
              ),
            ),
            const Spacer(),
            Icon(
              icon,
              color: color,
              size: 24,
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: isActive
                  ? AppTextStyles.labelMedium.copyWith(color: color, fontWeight: FontWeight.bold)
                  : AppTextStyles.labelSmall.copyWith(color: color),
            ),
            const Spacer(),
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
