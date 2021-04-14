import {Component, OnInit} from '@angular/core';
import {Observable, of} from 'rxjs';
import {map} from 'rxjs/operators';
import {TitlebarService} from '../../../service/titlebar.service';
import {ActivatedRoute} from '@angular/router';
import {CourseResultsService} from '../../../service/course-results.service';
import {CourseResult} from '../../../model/CourseResult';
import {Task} from '../../../model/Task';
import {SubmissionService} from '../../../service/submission.service';
import {AllSubmissionsComponent} from '../../../dialogs/all-submissions/all-submissions.component';
import {MatDialog} from '@angular/material/dialog';
import {TaskPointsService} from '../../../service/task-points.service';
import {Requirement} from '../../../model/Requirement';
import {TaskPointsDialogComponent} from '../../../dialogs/task-points-dialog/task-points-dialog.component';

/**
 * Matrix for every course docent a has
 */
@Component({
  selector: 'app-course-results',
  templateUrl: './course-results.component.html',
  styleUrls: ['./course-results.component.scss'],
})
export class CourseResultsComponent implements OnInit {
  courseId: number;
  courseResults: Observable<CourseResult[]> = of();
  tasks: Observable<Task[]> = of();
  requirements: Observable<Requirement[]> = of();
  bonusPoints = true;
  showDetails: boolean;

  constructor(private courseResultService: CourseResultsService, private tb: TitlebarService,
              private route: ActivatedRoute, private submissionService: SubmissionService,
              private dialog: MatDialog, private taskPointsService: TaskPointsService) {}

  ngOnInit(): void {
    this.tb.emitTitle('Dashboard');
    this.route.params.subscribe(param => {
      this.courseId = param.id;
      this.courseResults = this.courseResultService.getAllResults(this.courseId);
      this.tasks = this.courseResults.pipe(map(results => (results.length === 0) ? [] : results[0].results.map(result => result.task)));
      this.requirements = this.taskPointsService.getAllRequirements(this.courseId);
      this.requirements.subscribe(req => {
        console.log(req);
      });
    });
    // TODO: material progress spinner (cause the page might load for a while)
  }

  downloadResults() {
    // TODO
    // let data: Object[]
    // this.tasks
    //   .subscribe(tasks => {
    //     const taskNames: String[] = tasks.map(task => task.name)
    //     console.log(taskNames)
    //     this.courseResults.subscribe(results => {
    //      const temp = results.map(res => {})
    //     })
    //   })
  }

  showResult(uid: number, cid: number, tid: number) {
    this.submissionService.getAllSubmissions(uid, cid, tid)
      .subscribe(res => {
        this.dialog.open(AllSubmissionsComponent, {
          width: '100%',
          data: {
            submission: res
          },
        });
      });
    // TODO: show results
  }

  toggleDetails() {
      this.showDetails = !this.showDetails;
  }
}
