import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'l10n/app_localizations.dart';

import 'core/di/injector.dart';
import 'app/router.dart';
import 'app/theme.dart';
import 'services/api_client.dart';
import 'features/auth/presentation/bloc/auth_bloc.dart';
import 'features/auth/presentation/bloc/auth_event.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize dependency injection
  await setupLocator();
  
  runApp(const IslamicAiApp());
}

class IslamicAiApp extends StatefulWidget {
  const IslamicAiApp({super.key});

  @override
  State<IslamicAiApp> createState() => _IslamicAiAppState();
}

class _IslamicAiAppState extends State<IslamicAiApp> {
  late AuthBloc _authBloc;

  @override
  void initState() {
    super.initState();
    _authBloc = AuthBloc(getIt<ApiClient>())..add(AuthCheckEvent());
  }

  @override
  Widget build(BuildContext context) {
    final router = createRouter(_authBloc);

    return MultiBlocProvider(
      providers: [
        BlocProvider<AuthBloc>.value(value: _authBloc),
      ],
      child: MaterialApp.router(
        title: 'Islamic AI',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: ThemeMode.system,
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [
          Locale('en'),
          Locale('ru'),
          Locale('uz'),
          Locale.fromSubtags(languageCode: 'uz', scriptCode: 'Cyrl'),
        ],
        routerConfig: router,
      ),
    );
  }

  @override
  void dispose() {
    _authBloc.close();
    super.dispose();
  }
}
