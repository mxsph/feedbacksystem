import { Component, OnInit } from '@angular/core';
import {CheckerConfig} from "../../model/CheckerConfig";
import {Observable, of} from "rxjs"

import {CheckerService} from "../../service/checker.service";
import {ActivatedRoute} from "@angular/router";
import {NewCheckerDialogComponent} from "../../dialogs/new-checker-dialog/new-checker-dialog.component";
import {MatDialog} from "@angular/material/dialog";
import {MatSnackBar} from "@angular/material/snack-bar";
import {AuthService} from "../../service/auth.service";
import {Roles} from "../../model/Roles";

@Component({
  selector: 'app-configuration-list',
  templateUrl: './configuration-list.component.html',
  styleUrls: ['./configuration-list.component.scss']
})
export class ConfigurationListComponent implements OnInit {
  configurations: Observable<CheckerConfig[]> = of()
  courseId: number
  taskId: number

  constructor(private checkerService: CheckerService, private route: ActivatedRoute,
              private authService: AuthService,
              private dialog: MatDialog, private snackbar: MatSnackBar,) { }

  ngOnInit(): void {
    this.route.params.subscribe(
      params => {
        this.courseId = params.id
        this.taskId = params.tid
        this.configurations = this.checkerService.getChecker(this.courseId, this.taskId)
    });
  }

  isAuthorized(): boolean {
    const token = this.authService.getToken()
    const globalRole = token.globalRole
    const courseRole = token.courseRoles[this.courseId]
    return Roles.GlobalRole.isAdmin(globalRole)
      || Roles.GlobalRole.isModerator(globalRole)
      || Roles.CourseRole.isDocent(courseRole)
      || Roles.CourseRole.isTutor(courseRole)
  }

  addConfig() {
    this.dialog.open(NewCheckerDialogComponent, {
      height: 'auto',
      width: '50%',
      data: {
        courseId: this.courseId,
        taskId: this.taskId
      }
    }).afterClosed().subscribe(
      res => {
        if (res.success){
          this.snackbar.open('Überprüfung erfolgreich erstellt.', 'OK', {duration: 3000});
          this.configurations = this.checkerService.getChecker(this.courseId, this.taskId)
        }
      }, error => {
        console.error(error)
        this.snackbar.open('Überprüfung ändern hat nicht funktioniert.', 'OK', {duration: 3000});
      });
  }

  editConfig(checker: CheckerConfig) {
    console.log(checker);
    // this.checkerService.updateMainFile(this.courseId, this.taskId, checker.id, "test").subscribe(
    // )
    this.dialog.open(NewCheckerDialogComponent, {
      height: 'auto',
      width: '50%',
      data: {
        checker: checker,
        courseId: this.courseId,
        taskId: this.taskId
      }
    }).afterClosed().subscribe(
      res => {
        if (res.success){
          this.snackbar.open('Überprüfung erfolgreich geändert.', 'OK', {duration: 3000});
          this.configurations = this.checkerService.getChecker(this.courseId, this.taskId)
        }
      }, error => {
        console.error(error)
        this.snackbar.open('Überprüfung ändern hat nicht funktioniert.', 'OK', {duration: 3000});
      });
  }

  deleteConfig(checker: CheckerConfig) {
    this.snackbar.open("Soll die Überprüfung gelöscht werden?" , "Ja", {duration: 3000}).onAction()
      .subscribe( () => {
          this.checkerService.deleteChecker(this.courseId, this.taskId, checker.id)
            .subscribe( () => this.configurations = this.checkerService.getChecker(this.courseId, this.taskId))
        });
  }

  downloadMainFile(checker: CheckerConfig) {
    if (checker.mainFileUploaded){
      this.checkerService.getMainFile(this.courseId, this.taskId, checker.id)
    } else {
      this.snackbar.open("Es gibt keine Hauptdatei.", "OK", {duration: 3000})
    }
  }

  downloadSecondaryFile(checker: CheckerConfig) {
    if (checker.secondaryFileUploaded){
      this.checkerService.getSecondaryFile(this.courseId, this.taskId, checker.id)
    } else {
      this.snackbar.open("Es gibt keine Hauptdatei.", "OK", {duration: 3000})
    }
  }
}
