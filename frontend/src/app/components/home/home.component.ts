import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<div></div>`
})
export class HomeComponent implements OnInit {
  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit() {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login'], { replaceUrl: true });
    } else if (this.authService.isAdmin()) {
      this.router.navigate(['/admin'], { replaceUrl: true });
    } else {
      this.router.navigate(['/accounts'], { replaceUrl: true });
    }
  }
}
